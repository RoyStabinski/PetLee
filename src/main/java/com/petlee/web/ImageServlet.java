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
 * Serves the uploaded photographs at the {@code /images/...} URLs the DTOs advertise.
 *
 * <h2>Open, deliberately</h2>
 * A guest browsing the gallery has to see the pictures — specification §10's first scenario. The
 * private data in this application is the owner's contact details, which live behind
 * {@code GET /api/pets/{id}}'s session check, not the photographs.
 *
 * <h2>The path in the URL is attacker-controlled</h2>
 * Two things stand between {@code /images/....} and the filesystem:
 * <ol>
 *   <li>{@link ImageStore#resolve} rejects anything that does not start with its URL prefix, and
 *       normalises and re-checks containment against the upload root — the path-traversal guard.</li>
 *   <li>The first bytes of the file must match the signature its extension claims.</li>
 * </ol>
 * Any failure is a plain {@code 404}. Not a 403: telling a prober which of their guesses named a
 * real file, or which was merely rejected, is half the work of finding one.
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

    /**
     * One day. T-16 generates a fresh filename for every upload, so a URL's content never changes
     * — the only way to get different bytes is a different URL. That makes a long cache lifetime
     * free of the usual staleness problem.
     */
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
            // A file in the upload root whose bytes do not match its name. ImageStore cannot
            // produce one, so its presence means something else wrote there, and serving it with
            // a type the browser will trust is how a stored file becomes a stored script.
            LOGGER.log(Level.WARNING, () -> "Refusing to serve " + file + ": content does not match "
                    + extension);
            notFound(response);
            return;
        }

        response.setContentType(MEDIA_TYPES.get(extension));
        response.setHeader("Cache-Control", CACHE_CONTROL);
        // Belt and braces for a served-file endpoint: even with a verified signature, this stops a
        // browser from deciding a file is HTML because it happens to start with a tag later on.
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLengthLong(Files.size(file));

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
        }
    }

    /**
     * Turns the URL's path info into a readable file inside the upload root, or {@code null}.
     *
     * @param pathInfo everything after {@code /images}, including the leading slash; {@code null}
     *                 for a request to {@code /images} itself
     * <p>Package-private so the traversal defence can be exercised directly. Testing it through
     * {@code doGet} would mean faking a request and a response to assert on a status code that
     * this method already decided.
     *
     * @return the file to serve, or {@code null} if the path escapes the upload root or no such
     *         file exists
     */
    Path resolve(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        return images.resolve(ImageStore.URL_PREFIX + pathInfo.substring(1))
                .filter(Files::isRegularFile)
                .orElse(null);
    }

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

    /**
     * A bare 404 with no body. Never a directory listing, never the path that was tried, never a
     * stack trace: each of those tells a prober something about the filesystem behind the URL.
     */
    private static void notFound(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
