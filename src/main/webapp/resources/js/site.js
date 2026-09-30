/*
 * Small site-wide behaviour, loaded by the template on every page. Plain JavaScript, no library.
 *
 * 1. A link that plays a button (role="button", such as the favourite heart, which must be a
 *    link to hold its SVG) answers the Space key as a real button does; Enter already works.
 * 2. When a Faces ajax update replaces the element that had the focus (the heart re-renders
 *    itself), the focus is put back on its replacement, so a keyboard user is not dropped to
 *    the top of the page after every click.
 */
(function () {
    "use strict";

    document.addEventListener("keydown", function (event) {
        var target = event.target;
        if ((event.key === " " || event.key === "Spacebar") && target.tagName === "A"
                && target.getAttribute("role") === "button") {
            event.preventDefault(); // no page scroll
            target.click();
        }
    });

    var focusedId = null;

    function onAjax(data) {
        if (data.status === "begin") {
            var active = document.activeElement;
            focusedId = active && active.id ? active.id : null;
        } else if (data.status === "success" && focusedId) {
            var again = document.getElementById(focusedId);
            if (again && again !== document.activeElement) {
                again.focus();
            }
            focusedId = null;
        }
    }

    // faces.js is loaded by Faces in the head only on pages with ajax; elsewhere there is
    // nothing to follow.
    function register() {
        if (window.faces && faces.ajax && faces.ajax.addOnEvent) {
            faces.ajax.addOnEvent(onAjax);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", register);
    } else {
        register();
    }
}());
