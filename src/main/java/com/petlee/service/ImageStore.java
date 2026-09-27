package com.petlee.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stores, deletes and resolves the single photograph a pet may carry. */
@ApplicationScoped
public class ImageStore {

    public static final String URL_PREFIX = "/images/";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/gif", "gif", "image/webp", "webp");

    private final Path root = Path.of(Optional.ofNullable(System.getProperty("petlee.upload.dir"))
            .or(() -> Optional.ofNullable(System.getenv("PETLEE_UPLOAD_DIR")))
            .orElse(System.getProperty("user.home") + "/petlee-uploads")).normalize();

    /**
     * Saves an uploaded photograph under a generated name.
     *
     * <p>A stream has no size up front, so the limit is enforced while copying: at most
     * {@code MAX_BYTES + 1} bytes are read, and reaching that one extra byte means the photograph
     * is too large. The partial file is deleted on every failure. The stream is read but not
     * closed; it belongs to the caller.
     *
     * @param content     the photograph's bytes
     * @param contentType its media type, such as {@code image/png}; parameters are ignored
     * @return the URL to store on the pet
     * @throws AppException 400 if it is missing, empty, too large, or not a supported image type
     */
    public String store(InputStream content, String contentType) {
        if (content == null) {
            throw new AppException(400, "A photo file is required");
        }
        String extension = EXTENSIONS.get(baseType(contentType));
        if (extension == null) {
            throw new AppException(400, "The photo must be a JPEG, PNG, GIF or WebP image");
        }

        Path target = root.resolve(UUID.randomUUID() + "." + extension);
        long copied;
        try {
            Files.createDirectories(root);
            try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                copied = copyAtMost(content, out, MAX_BYTES + 1);
            }
        } catch (IOException e) {
            deleteQuietly(target);
            throw new AppException(500, "The photo could not be saved");
        }

        if (copied > MAX_BYTES) {
            deleteQuietly(target);
            throw new AppException(400, "The photo must be 5 MB or smaller");
        }
        if (copied == 0) {
            deleteQuietly(target);
            throw new AppException(400, "A photo file is required");
        }
        return URL_PREFIX + target.getFileName();
    }

    /**
     * Copies until the input ends or {@code limit} bytes have been copied, whichever is first.
     *
     * @return the number of bytes copied, at most {@code limit}
     */
    private static long copyAtMost(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        while (total < limit) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, limit - total));
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    /** {@code "image/PNG; foo=bar"} to {@code "image/png"}; null stays unmatched. */
    private static String baseType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String type = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // an orphaned file is not worth hiding the real failure over
        }
    }

    /** Deletes the file behind a photograph URL, if it is still there. */
    public void delete(String url) {
        resolve(url).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // an orphaned file is not worth failing the request over
            }
        });
    }

    /**
     * Maps a photograph URL back to a file.
     *
     * @param url the URL stored on a pet
     * @return the file, or empty if the URL is foreign or escapes the upload root
     */
    public Optional<Path> resolve(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return Optional.empty();
        }
        Path candidate = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        return candidate.startsWith(root) ? Optional.of(candidate) : Optional.empty();
    }
}
