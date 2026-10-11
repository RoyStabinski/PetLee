/*
 * The focal-point editor on editPet.xhtml (each photograph) and editProfile.xhtml (the profile
 * photo). Plain JavaScript, no library, versioned with ?v= like the stylesheet.
 *
 * The owner shows where the subject of a photo is - a face, say - and every cropped view of it
 * (object-fit: cover) is then drawn with object-position at that point. The file is never changed.
 *
 * The markup it expects:
 *   [data-focus-open]     a button per photo, with data-focus-src, data-focus-x, data-focus-y and,
 *                         for a pet photograph, data-focus-id. Starts hidden: this script reveals
 *                         it, so without JavaScript there is no dead button, and the photos still
 *                         show with their saved crop.
 *   [data-focus-panel]    the editor, hidden until a photo is opened, holding:
 *     [data-focus-stage]    the frame the whole photo is shown in (object-fit: contain), with
 *     [data-focus-image]    the photo and
 *     [data-focus-marker]   the ring: dragged, or placed by a click anywhere on the photo, or
 *                           moved with the arrow keys (5% a press, 1% with Shift)
 *     [data-focus-preview]  an img showing the crop as it will look
 *     [data-focus-status]   a live region announcing the position
 *     [data-focus-field]    the hidden inputs "id", "x" and "y" the Faces form submits
 *     [data-focus-cancel]   closes the panel without saving
 *
 * Save is a Faces command button with f:ajax, whose onevent is PetLeeFocus.onSave: once the
 * response has re-rendered the tiles, the panel closes and the focus goes back to the photo's
 * button. A 401 comes back as a redirect to the login page, which faces.js follows.
 */
(function () {
    "use strict";

    var KEY_STEP = 5;
    var FINE_STEP = 1;

    var panel, stage, image, marker, preview, status, fields = {};
    var opener = null;      // the button that opened the panel, to return the focus to
    var openId = null;      // the photograph being edited, to find its button after a re-render
    var x = 50;
    var y = 50;
    var dragging = false;

    function clamp(value) {
        return Math.max(0, Math.min(100, Math.round(value)));
    }

    /** Where the picture actually is inside its object-fit: contain frame, in stage pixels. */
    function pictureBox() {
        var frameW = image.clientWidth;
        var frameH = image.clientHeight;
        var naturalW = image.naturalWidth || frameW;
        var naturalH = image.naturalHeight || frameH;
        var scale = Math.min(frameW / naturalW, frameH / naturalH);
        var w = naturalW * scale;
        var h = naturalH * scale;
        return {
            left: image.offsetLeft + (frameW - w) / 2,
            top: image.offsetTop + (frameH - h) / 2,
            width: w,
            height: h
        };
    }

    function render() {
        var box = pictureBox();
        marker.style.left = (box.left + box.width * x / 100) + "px";
        marker.style.top = (box.top + box.height * y / 100) + "px";
        preview.style.objectPosition = x + "% " + y + "%";
        fields.x.value = String(x);
        fields.y.value = String(y);
        // A button has no value to announce, so its name carries the position: tabbing to it
        // says where it is.
        marker.setAttribute("aria-label", "Focal point, " + x + "% from the left, " + y + "% from the top");
    }

    function describe() {
        return "Focus " + x + "% from the left, " + y + "% from the top";
    }

    function announce() {
        status.textContent = describe();
    }

    function moveTo(newX, newY) {
        x = clamp(newX);
        y = clamp(newY);
        render();
    }

    /** Places the marker under a pointer, in percent of the picture, clamped to its edges. */
    function moveToPointer(event) {
        var box = pictureBox();
        var rect = stage.getBoundingClientRect();
        moveTo((event.clientX - rect.left - box.left) / box.width * 100,
               (event.clientY - rect.top - box.top) / box.height * 100);
    }

    function revealOpeners() {
        Array.prototype.forEach.call(document.querySelectorAll("[data-focus-open]"), function (button) {
            button.hidden = false;
            button.setAttribute("aria-expanded", String(!panel.hidden && button === opener));
        });
    }

    function open(button) {
        opener = button;
        openId = button.getAttribute("data-focus-id");
        x = clamp(Number(button.getAttribute("data-focus-x")));
        y = clamp(Number(button.getAttribute("data-focus-y")));
        if (fields.id) {
            fields.id.value = openId || "";
        }
        var src = button.getAttribute("data-focus-src");
        image.src = src;
        preview.src = src;
        panel.hidden = false;
        revealOpeners();
        // The marker can only be placed once the picture's proportions are known.
        if (image.complete && image.naturalWidth) {
            render();
        } else {
            image.addEventListener("load", render, { once: true });
        }
        status.textContent = "";
        panel.scrollIntoView({ block: "nearest" });
        marker.focus({ preventScroll: true });
    }

    function close() {
        panel.hidden = true;
        revealOpeners();
        if (opener && document.body.contains(opener)) {
            opener.focus();
        }
        opener = null;
    }

    /** f:ajax onevent of the Save button. */
    function onSave(data) {
        if (data.status !== "success") {
            return;
        }
        // The tiles were re-rendered, so the old button is gone: find its replacement.
        var selector = openId ? "[data-focus-open][data-focus-id='" + openId + "']" : "[data-focus-open]";
        opener = document.querySelector(selector) || opener;
        close();
    }

    function init() {
        panel = document.querySelector("[data-focus-panel]");
        if (!panel) {
            return;
        }
        stage = panel.querySelector("[data-focus-stage]");
        image = panel.querySelector("[data-focus-image]");
        marker = panel.querySelector("[data-focus-marker]");
        preview = panel.querySelector("[data-focus-preview]");
        status = panel.querySelector("[data-focus-status]");
        Array.prototype.forEach.call(panel.querySelectorAll("[data-focus-field]"), function (input) {
            fields[input.getAttribute("data-focus-field")] = input;
        });

        // Delegated, so the buttons a Faces re-render puts in place work too.
        document.addEventListener("click", function (event) {
            var button = event.target.closest("[data-focus-open]");
            if (button) {
                open(button);
            } else if (event.target.closest("[data-focus-cancel]")) {
                close();
            }
        });

        stage.addEventListener("pointerdown", function (event) {
            if (event.button !== 0) {
                return;
            }
            event.preventDefault(); // no text selection or image drag while placing the ring
            dragging = true;
            stage.setPointerCapture(event.pointerId);
            marker.focus({ preventScroll: true });
            moveToPointer(event);
        });
        stage.addEventListener("pointermove", function (event) {
            if (dragging) {
                moveToPointer(event);
            }
        });
        function endDrag() {
            if (dragging) {
                dragging = false;
                announce(); // once, at the end, not for every pixel of the drag
            }
        }
        stage.addEventListener("pointerup", endDrag);
        stage.addEventListener("pointercancel", endDrag);

        marker.addEventListener("keydown", function (event) {
            var step = event.shiftKey ? FINE_STEP : KEY_STEP;
            var moves = {
                ArrowLeft: [-step, 0],
                ArrowRight: [step, 0],
                ArrowUp: [0, -step],
                ArrowDown: [0, step]
            };
            var move = moves[event.key];
            if (move) {
                event.preventDefault(); // no page scroll
                moveTo(x + move[0], y + move[1]);
                announce();
            }
        });

        panel.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                close();
            }
        });

        window.addEventListener("resize", function () {
            if (!panel.hidden) {
                render();
            }
        });

        revealOpeners();
        if (window.faces && faces.ajax && faces.ajax.addOnEvent) {
            // Any other ajax update that re-renders the tiles brings back hidden buttons.
            faces.ajax.addOnEvent(function (data) {
                if (data.status === "success") {
                    revealOpeners();
                }
            });
        }
    }

    window.PetLeeFocus = { onSave: onSave };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
}());
