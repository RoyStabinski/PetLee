package com.petlee.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-16's storage configuration.
 *
 * <p>The environment-variable branch is not exercised here: a JVM cannot set its own environment,
 * and a test that shelled out to prove it would be testing the shell. It is one
 * {@code System.getenv} call between two branches that are covered.
 */
class StorageConfigTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty(StorageConfig.UPLOAD_DIR_PROPERTY);
        System.clearProperty("com.sun.aas.instanceRoot");
    }

    @Test
    @DisplayName("the system property wins, and the directory is created if it is missing")
    void resolvesTheSystemPropertyFirst() throws IOException {
        Path configured = tempDir.resolve("uploads");
        System.setProperty(StorageConfig.UPLOAD_DIR_PROPERTY, configured.toString());

        StorageConfig config = new StorageConfig();
        config.init();

        assertEquals(configured.toRealPath(), config.getUploadRoot());
        assertTrue(Files.isDirectory(config.getUploadRoot()),
                "a misconfigured path must fail at deployment, not at the first user's upload");
    }

    @Test
    @DisplayName("with no configuration, uploads go beside the server's domain directory")
    void fallsBackToADirectoryBesideTheDomain() throws IOException {
        // Payara's instanceRoot is .../domains/domain1, so the default is .../domains/petlee-uploads:
        // beside it, not inside, because a domain directory is the server's and is recreated whole.
        Path domain = tempDir.resolve("domains").resolve("domain1");
        System.setProperty("com.sun.aas.instanceRoot", domain.toString());

        StorageConfig config = new StorageConfig();
        config.init();

        assertEquals(tempDir.resolve("domains").resolve(StorageConfig.DEFAULT_DIRECTORY_NAME).toRealPath(),
                config.getUploadRoot());
    }

    @Test
    @DisplayName("an unusable path fails loudly, naming the two settings that fix it")
    void reportsAnUnusablePath() {
        Path notADirectory = tempDir.resolve("a-file");
        try {
            Files.writeString(notADirectory, "not a directory");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        System.setProperty(StorageConfig.UPLOAD_DIR_PROPERTY, notADirectory.resolve("under-a-file").toString());

        StorageConfig config = new StorageConfig();
        RuntimeException e = assertThrows(RuntimeException.class, config::init);

        assertTrue(e.getMessage().contains(StorageConfig.UPLOAD_DIR_PROPERTY), e.getMessage());
        assertTrue(e.getMessage().contains(StorageConfig.UPLOAD_DIR_ENV), e.getMessage());
    }

    @Test
    @DisplayName("the public URL prefix is what the database stores, not a filesystem path")
    void exposesThePublicUrlPrefix() {
        assertEquals("/images/", StorageConfig.PUBLIC_URL_PREFIX);
    }
}
