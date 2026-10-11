/*
 * The round profile-photo tiles on register.xhtml and editProfile.xhtml. The file input inside
 * each tile is visually hidden; this previews the chosen file in the circle before anything is
 * uploaded, and refuses a file that is not an image or is over 5 MB at once, rather than after
 * the whole form has been sent. Without JavaScript the upload still works, there is just no
 * preview, and the server refuses such a file instead.
 *
 * A chosen file is previewed centred, since an upload resets the focal point; the saved photo's
 * own crop (its inline object-position) comes back if the choice is undone. After a Faces ajax
 * update that replaced a tile (the focus editor re-renders it), the new tile is set up again.
 */
(function () {
    "use strict";

    var MAX_BYTES = 5 * 1024 * 1024;
    var TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"];

    function initPicker(picker) {
        var input = picker.querySelector("input[type=file]");
        var img = picker.querySelector("[data-avatar-img]");
        var placeholder = picker.querySelector("[data-avatar-placeholder]");
        var caption = picker.querySelector("[data-avatar-caption]");
        if (!input || !img || !caption || picker.hasAttribute("data-avatar-ready")) {
            return;
        }
        picker.setAttribute("data-avatar-ready", "");

        var originalSrc = img.getAttribute("src");
        var originalPosition = img.style.objectPosition;
        var originalCaption = caption.textContent;
        var objectUrl = null;

        function release() {
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
                objectUrl = null;
            }
        }

        function showOriginal(message) {
            release();
            if (originalSrc) {
                img.src = originalSrc;
                img.style.objectPosition = originalPosition;
                img.hidden = false;
            } else {
                img.removeAttribute("src");
                img.hidden = true;
            }
            if (placeholder) {
                placeholder.hidden = Boolean(originalSrc);
            }
            caption.textContent = message || originalCaption;
            picker.classList.toggle("has-error", Boolean(message));
            picker.classList.remove("has-files");
        }

        input.addEventListener("change", function () {
            var file = input.files && input.files[0];
            if (!file) {
                showOriginal();
                return;
            }
            if (TYPES.indexOf(file.type) < 0) {
                input.value = "";
                showOriginal("Choose a JPEG, PNG, GIF or WebP image");
                return;
            }
            if (file.size > MAX_BYTES) {
                input.value = "";
                showOriginal("That photo is over 5 MB");
                return;
            }
            release();
            objectUrl = URL.createObjectURL(file);
            img.src = objectUrl;
            img.style.objectPosition = "";
            img.hidden = false;
            if (placeholder) {
                placeholder.hidden = true;
            }
            caption.textContent = "Change photo";
            picker.classList.remove("has-error");
            picker.classList.add("has-files");
        });
    }

    function initAll() {
        Array.prototype.forEach.call(document.querySelectorAll("[data-avatar-picker]"), initPicker);
    }

    function start() {
        initAll();
        // faces.js is on the page only where there is ajax.
        if (window.faces && faces.ajax && faces.ajax.addOnEvent) {
            faces.ajax.addOnEvent(function (data) {
                if (data.status === "success") {
                    initAll();
                }
            });
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
}());
