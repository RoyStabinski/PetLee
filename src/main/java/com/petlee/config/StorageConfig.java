package com.petlee.config;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Where uploaded photographs live.
 *
 * <h2>The directory must be outside the deployment</h2>
 * A server explodes a WAR into a work directory and deletes that directory on redeploy. An upload
 * root inside it would mean every redeploy silently destroys the users' photographs, and nothing
 * would report it — the database rows would survive and every image would 404. So the root is
 * resolved outside the deployment, and never relative to the servlet context.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>The system property {@code petlee.upload.dir} — the one a server start-up script can set
 *       without touching the environment.</li>
 *   <li>The environment variable {@code PETLEE_UPLOAD_DIR}.</li>
 *   <li>A {@code petlee-uploads} directory <em>beside</em> the server's domain directory, found
 *       through whichever of the well-known server properties is set. Beside, not inside: a domain
 *       directory is the server's, and an upload root inside one is lost the first time the domain
 *       is recreated.</li>
 *   <li>Failing all of those — a plain JVM, as in a unit test — {@code petlee-uploads} under the
 *       user's home directory.</li>
 * </ol>
 *
 * <p>See {@code docs/deployment/upload-directory.md}. The project has no properties file to hold
 * this (ADR-003 keeps the dependency list closed and nothing loads one), so the documentation is
 * the deployment guide rather than a {@code application.properties} that no code would read.
 */
@ApplicationScoped
public class StorageConfig {

    private static final Logger LOGGER = Logger.getLogger(StorageConfig.class.getName());

    /** Settable from a server start-up script without touching the environment. */
    public static final String UPLOAD_DIR_PROPERTY = "petlee.upload.dir";

    /** The environment variable T-42's runbook documents. */
    public static final String UPLOAD_DIR_ENV = "PETLEE_UPLOAD_DIR";

    /** The directory name used by the last two fallbacks. */
    static final String DEFAULT_DIRECTORY_NAME = "petlee-uploads";

    /**
     * Each server names its domain directory differently. Order matters only in that the first one
     * set wins, and no server sets two of them.
     */
    private static final String[] SERVER_DOMAIN_PROPERTIES = {
            "com.sun.aas.instanceRoot",   // Payara 6, GlassFish 7
            "jboss.server.base.dir",      // WildFly
            "catalina.base"               // Tomcat / TomEE
    };

    /** The public URL prefix the database stores and T-23 serves. */
    public static final String PUBLIC_URL_PREFIX = "/images/";

    private Path uploadRoot;

    /**
     * Resolves the root and creates it, once, at start-up.
     *
     * <p>Creating it here rather than on first upload means a misconfigured path fails at
     * deployment, where someone is watching, instead of at the first user's upload.
     */
    @PostConstruct
    void init() {
        uploadRoot = resolveUploadRoot();
        LOGGER.log(Level.INFO, () -> "Upload root: " + uploadRoot);
    }

    /**
     * @return the absolute, real path of the directory uploads are written to. Every path derived
     *         from it is checked against it again before use — see
     *         {@code ImageStorageService.resolveInsideRoot}.
     */
    public Path getUploadRoot() {
        if (uploadRoot == null) {
            // A plain JVM outside CDI never fires @PostConstruct. Resolving lazily here keeps the
            // class usable in a unit test without making the container path lazy too.
            init();
        }
        return uploadRoot;
    }

    private Path resolveUploadRoot() {
        Path configured = fromSystemProperty();
        if (configured == null) {
            configured = fromEnvironment();
        }
        if (configured == null) {
            configured = besideTheServerDomain();
        }
        if (configured == null) {
            configured = Paths.get(System.getProperty("user.home"), DEFAULT_DIRECTORY_NAME);
        }
        return create(configured);
    }

    private static Path fromSystemProperty() {
        return toPath(System.getProperty(UPLOAD_DIR_PROPERTY));
    }

    private static Path fromEnvironment() {
        return toPath(System.getenv(UPLOAD_DIR_ENV));
    }

    private static Path besideTheServerDomain() {
        for (String property : SERVER_DOMAIN_PROPERTIES) {
            Path domain = toPath(System.getProperty(property));
            if (domain != null) {
                Path parent = domain.toAbsolutePath().getParent();
                Path base = parent != null ? parent : domain.toAbsolutePath();
                return base.resolve(DEFAULT_DIRECTORY_NAME);
            }
        }
        return null;
    }

    private static Path toPath(String value) {
        return value == null || value.isBlank() ? null : Paths.get(value.trim());
    }

    private static Path create(Path candidate) {
        try {
            Files.createDirectories(candidate);
            // toRealPath resolves symlinks and "..", so the containment check in
            // ImageStorageService compares two canonical paths rather than two spellings of one.
            return candidate.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot create or read the upload directory " + candidate
                            + ". Set " + UPLOAD_DIR_PROPERTY + " or " + UPLOAD_DIR_ENV
                            + " to a writable path outside the deployment.", e);
        }
    }
}
