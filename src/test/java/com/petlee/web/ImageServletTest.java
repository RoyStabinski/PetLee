package com.petlee.web;

import com.petlee.config.StorageConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-23 criterion 4: the traversal defence, which the task requires to be an automated test rather
 * than a demonstrated {@code curl}.
 *
 * <p>It is a test and not just a {@code curl} for a reason. A server may reject
 * {@code %2e%2e%2f} at its connector before the application sees it — Payara answers 400 to that
 * request and this servlet is never called — so the manual check can pass while the code behind it
 * is wrong. These assertions go straight at {@link ImageServlet#resolve}, which is what would face
 * the request on a server that decoded and normalised more quietly.
 */
class ImageServletTest {

    /** The shape T-16 generates: pet id, underscore, UUID, extension. */
    private static final String STORED_NAME = "185_12151c2b-e287-460b-8028-16e57a2d1ab1.jpg";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};

    @TempDir
    Path tempDir;

    private ImageServlet servlet;
    private Path uploadRoot;

    @BeforeEach
    void setUp() throws IOException {
        Path configured = tempDir.resolve("uploads");
        System.setProperty(StorageConfig.UPLOAD_DIR_PROPERTY, configured.toString());

        StorageConfig storage = new StorageConfig();
        uploadRoot = storage.getUploadRoot();

        // A secret beside the upload root - the thing a traversal is trying to reach.
        Files.writeString(tempDir.resolve("secret.txt"), "the password is hunter2");
        Files.write(uploadRoot.resolve(STORED_NAME), JPEG);

        servlet = new ImageServlet();
        servlet.setStorage(storage);
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty(StorageConfig.UPLOAD_DIR_PROPERTY);
    }

    @Test
    @DisplayName("a name T-16 generated resolves to the file inside the upload root")
    void servesAStoredFile() {
        Path resolved = servlet.resolve("/" + STORED_NAME);

        assertNotNull(resolved);
        assertEquals(uploadRoot.resolve(STORED_NAME), resolved);
        assertTrue(resolved.startsWith(uploadRoot));
    }

    @ParameterizedTest(name = "{0} is refused")
    @DisplayName("criterion 4 — nothing that walks out of the upload root resolves")
    @ValueSource(strings = {
            "/../../../../etc/passwd",
            "/../secret.txt",
            "/..%2f..%2fsecret.txt",
            "/%2e%2e%2f%2e%2e%2fsecret.txt",
            "/%2E%2E/secret.txt",
            "/..\\..\\secret.txt",
            "/....//secret.txt",
            "/C:\\windows\\win.ini",
            "//etc/passwd",
            "/subdir/" + STORED_NAME,
            "/" + STORED_NAME + "/../../secret.txt"
    })
    void refusesEverythingThatEscapes(String pathInfo) {
        // The encoded forms are here even though a container decodes before getPathInfo: the
        // defence must not depend on which of them the server in front of it happens to reject.
        assertNull(servlet.resolve(pathInfo));
    }

    @ParameterizedTest(name = "{0} is refused")
    @DisplayName("names that are not the generated shape are refused, whatever they point at")
    @ValueSource(strings = {
            "/does-not-exist.jpg",
            "/secret.txt",
            "/evil.sh",
            "/185_12151c2b-e287-460b-8028-16e57a2d1ab1.exe",
            "/185_12151c2b-e287-460b-8028-16e57a2d1ab1.jpg.exe",
            "/"
    })
    void refusesNamesOutsideTheGeneratedShape(String pathInfo) {
        assertNull(servlet.resolve(pathInfo));
    }

    @Test
    @DisplayName("a request for /images itself is refused rather than listed")
    void refusesTheDirectoryItself() {
        assertNull(servlet.resolve(null));
        assertNull(servlet.resolve(""));
    }

    @Test
    @DisplayName("a file whose bytes do not match its extension is not served")
    void refusesAFileThatIsNotTheImageItClaims() throws IOException {
        Path liar = uploadRoot.resolve("186_12151c2b-e287-460b-8028-16e57a2d1ab1.jpg");
        Files.writeString(liar, "<script>alert(1)</script>");

        // resolve() accepts the name - it is the right shape and the file exists - and the
        // signature check is what stops it being served as an image.
        assertNotNull(servlet.resolve("/" + liar.getFileName()));
        assertFalse(ImageServlet.matchesSignature(liar, "jpg"));
        assertTrue(ImageServlet.matchesSignature(uploadRoot.resolve(STORED_NAME), "jpg"));
    }
}
