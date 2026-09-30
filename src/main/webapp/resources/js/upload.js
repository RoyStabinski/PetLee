/*
 * The dashed "Add photo" tiles on addPet.xhtml and editPet.xhtml. The file input inside each
 * tile is visually hidden, so it cannot show what was chosen; this says it in the tile's caption.
 * Without JavaScript the upload still works, the caption just stays "Add photo".
 */
(function () {
    "use strict";

    function initTile(input) {
        var tile = input.closest(".upload-tile");
        var caption = tile && tile.querySelector(".upload-tile-text");
        if (!caption) {
            return;
        }
        input.addEventListener("change", function () {
            var count = input.files ? input.files.length : 0;
            caption.textContent = count === 0 ? "Add photo"
                : count === 1 ? "1 photo chosen" : count + " photos chosen";
            tile.classList.toggle("has-files", count > 0);
        });
    }

    function initAll() {
        Array.prototype.forEach.call(document.querySelectorAll(".upload-input"), initTile);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initAll);
    } else {
        initAll();
    }
}());
