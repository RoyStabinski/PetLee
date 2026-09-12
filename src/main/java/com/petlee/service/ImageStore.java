package com.petlee.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where a pet's single photograph lives, and how it is stored, deleted and resolved back to a
 * file — the whole of what a pet's storage layer needs now that it has one photograph instead
 * of a gallery.
 */
@ApplicationScoped
public class ImageStore {

    public static final String URL_PREFIX = "/images/";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/gif", "gif", "image/webp", "webp");

    private final Path root = Path.of(Optional.ofNullable(System.getProperty("petlee.upload.dir"))
            .or(() -> Optional.ofNullable(System.getenv("PETLEE_UPLOAD_DIR")))
            .orElse(System.getProperty("user.home") + "/petlee-uploads")).normalize();

    public String store(Part part) {
        if (part == null || part.getSize() == 0) {
            throw new AppException(400, "A photo file is required");
        }
        if (part.getSize() > MAX_BYTES) {
            throw new AppException(400, "The photo must be 5 MB or smaller");
        }
        String extension = EXTENSIONS.get(
                String.valueOf(part.getContentType()).toLowerCase(Locale.ROOT));
        if (extension == null) {
            throw new AppException(400, "The photo must be a JPEG, PNG, GIF or WebP image");
        }
        String fileName = UUID.randomUUID() + "." + extension;
        try (InputStream in = part.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(in, root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new AppException(500, "The photo could not be saved");
        }
        return URL_PREFIX + fileName;
    }

    public void delete(String url) {
        resolve(url).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // an orphaned file is not worth failing the request over
            }
        });
    }

    public Optional<Path> resolve(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return Optional.empty();
        }
        Path candidate = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        return candidate.startsWith(root) ? Optional.of(candidate) : Optional.empty();
    }
}
