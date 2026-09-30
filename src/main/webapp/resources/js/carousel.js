/*
 * Pet-Lee's one photo carousel, used by the details page and by every gallery card. Plain
 * JavaScript, no library, served from the application with a ?v= version like the stylesheet.
 *
 * Markup contract - everything is found through data attributes inside a [data-carousel] root;
 * only the slides are required, the rest is optional:
 *   [data-carousel-slide]    the photos, in order; the one with class is-current is shown
 *   [data-carousel-prev]     previous button   } start with the hidden attribute: this script
 *   [data-carousel-next]     next button       } reveals them, so without JavaScript only the
 *   [data-carousel-counter]  "2 / 5" pill      } first photo shows and nothing is dead
 *   [data-carousel-current]  the number inside the counter
 *   [data-carousel-dot]      one button per photo (also starts hidden, in a hidden container)
 *   [data-carousel-dots]     the dots' container
 *   [data-carousel-thumb]    one link per photo, to its full-size image (works without JS)
 *   [data-carousel-track]    the scrolling thumbnail row, and
 *   [data-carousel-more]     its "scroll on" button, shown only while the row overflows
 *
 * With fewer than two slides the script does nothing, and the server renders no controls.
 */
(function () {
    "use strict";

    function all(root, name) {
        return Array.prototype.slice.call(root.querySelectorAll("[data-carousel-" + name + "]"));
    }

    function one(root, name) {
        return root.querySelector("[data-carousel-" + name + "]");
    }

    function initCarousel(root) {
        var slides = all(root, "slide");
        if (slides.length < 2 || root.hasAttribute("data-carousel-ready")) {
            return;
        }
        root.setAttribute("data-carousel-ready", "");
        var prev = one(root, "prev");
        var next = one(root, "next");
        var counter = one(root, "counter");
        var current = one(root, "current");
        var dotsBox = one(root, "dots");
        var dots = all(root, "dot");
        var thumbs = all(root, "thumb");
        var track = one(root, "track");
        var more = one(root, "more");
        var index = Math.max(0, slides.findIndex(function (s) { return s.classList.contains("is-current"); }));

        function mark(list, i) {
            list.forEach(function (el, j) {
                el.setAttribute("aria-current", j === i ? "true" : "false");
            });
        }

        function show(target) {
            // Wraps both ways: after the last photo comes the first, and before the first the last.
            index = (target + slides.length) % slides.length;
            slides.forEach(function (slide, j) {
                slide.classList.toggle("is-current", j === index);
            });
            mark(thumbs, index);
            mark(dots, index);
            if (current) {
                current.textContent = String(index + 1);
            }
            var thumb = thumbs[index];
            if (thumb && track) {
                // Keeps the highlighted thumbnail in view without scrolling the page. The track is
                // position: relative in the stylesheet, so offsetLeft is measured from its edge.
                var item = thumb.closest("li") || thumb;
                var left = item.offsetLeft;
                if (left < track.scrollLeft || left + item.offsetWidth > track.scrollLeft + track.clientWidth) {
                    track.scrollTo({ left: left - track.clientWidth / 2 + item.offsetWidth / 2, behavior: "smooth" });
                }
            }
        }

        // A control inside a card must not also count as a click on the card or its links.
        function control(el, handler) {
            if (!el) {
                return;
            }
            el.addEventListener("click", function (event) {
                event.preventDefault();
                event.stopPropagation();
                handler();
            });
        }

        control(prev, function () { show(index - 1); });
        control(next, function () { show(index + 1); });
        dots.forEach(function (dot, j) { control(dot, function () { show(j); }); });
        thumbs.forEach(function (thumb, j) { control(thumb, function () { show(j); }); });

        root.addEventListener("keydown", function (event) {
            if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
                event.preventDefault();
                event.stopPropagation();
                show(index + (event.key === "ArrowLeft" ? -1 : 1));
            }
        });

        if (track && more) {
            var updateMore = function () {
                more.hidden = track.scrollWidth <= track.clientWidth + 1;
            };
            // From the end of the row it goes back to the start.
            control(more, function () {
                var atEnd = track.scrollLeft + track.clientWidth >= track.scrollWidth - 1;
                track.scrollTo({ left: atEnd ? 0 : track.scrollLeft + track.clientWidth * 0.8, behavior: "smooth" });
            });
            window.addEventListener("resize", updateMore);
            updateMore();
        }

        [prev, next, counter, dotsBox].concat(dots).forEach(function (el) {
            if (el) {
                el.hidden = false;
            }
        });
        show(index);
    }

    function initAll() {
        Array.prototype.forEach.call(document.querySelectorAll("[data-carousel]"), initCarousel);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initAll);
    } else {
        initAll();
    }

    // The gallery grid is re-rendered by Faces ajax (Apply, Clear): set up the new cards too.
    // data-carousel-ready keeps a carousel that survived the update from being set up twice.
    if (window.faces && faces.ajax && faces.ajax.addOnEvent) {
        faces.ajax.addOnEvent(function (data) {
            if (data.status === "success") {
                initAll();
            }
        });
    }
}());
