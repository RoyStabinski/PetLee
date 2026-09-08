package com.petlee.web;

import com.petlee.service.ImageStore;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves uploaded photographs at {@code /images/...}. Open, since a guest browsing the gallery
 * has to see them.
 *
 * <p>The path is attacker-controlled, so two guards stand in front of the filesystem:
 * {@link ImageStore#resolve} normalises and re-checks containment in the upload root, and the
 * file's leading bytes must match the signature its extension claims. Either failure is a bare
 * 404, never a 403 — which of a prober's guesses named a real file is not their business.
 */
@WebServlet(name = "ImageServlet", urlPatterns = "/images/*")
public class ImageServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ImageServlet.class.getName());

    /** Extension to media type — the same four formats {@link ImageStore} accepts. */
    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp");

    /** Extension to the file's leading bytes, checked before anything is written to the client. */
    private static final Map<String, int[]> SIGNATURES = Map.of(
            "jpg", new int[]{0xFF, 0xD8, 0xFF},
            "png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
            "gif", new int[]{0x47, 0x49, 0x46, 0x38},
            "webp", new int[]{0x52, 0x49, 0x46, 0x46});

    /** One day — safe, because every upload gets a fresh filename, so a URL never changes. */
    private static final String CACHE_CONTROL = "public, max-age=86400";

    private ImageStore images;

    /** For the container, which instantiates a servlet with a no-argument constructor. */
    public ImageServlet() {
    }

    @Inject
    public void setImages(ImageStore images) {
        this.images = images;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Path file = resolve(request.getPathInfo());
        if (file == null) {
            notFound(response);
            return;
        }

        String extension = extensionOf(file.getFileName().toString());
        if (!matchesSignature(file, extension)) {
            // ImageStore cannot produce such a file, so something else wrote to the upload root.
            LOGGER.log(Level.WARNING, () -> "Refusing to serve " + file + ": content does not match "
                    + extension);
            notFound(response);
            return;
        }

        response.setContentType(MEDIA_TYPES.get(extension));
        response.setHeader("Cache-Control", CACHE_CONTROL);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLengthLong(Files.size(file));

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
        }
    }

    /**
     * Turns the URL's path info into a readable file inside the upload root.
     *
     * @param pathInfo everything after {@code /images}, including the leading slash
     * @return the file to serve, or null if it escapes the upload root or does not exist
     */
    Path resolve(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        return images.resolve(ImageStore.URL_PREFIX + pathInfo.substring(1))
                .filter(Files::isRegularFile)
                .orElse(null);
    }

    /**
     * @param file      the file about to be served
     * @param extension its extension, lowercased
     * @return whether the file's leading bytes match the format the extension claims
     */
    static boolean matchesSignature(Path file, String extension) {
        int[] signature = SIGNATURES.get(extension);
        if (signature == null) {
            return false;
        }

        try (var stream = Files.newInputStream(file)) {
            byte[] head = stream.readNBytes(signature.length);
            if (head.length < signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                if ((head[i] & 0xFF) != signature[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static String extensionOf(String name) {
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** A bare 404: no body, no path, nothing about the filesystem behind the URL. */
    private static void notFound(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
