package com.petlee.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * The Jakarta REST entry point: it puts every resource class under {@code /api}, which is the
 * {@code Base path: /api} that {@code api-contract.md} freezes.
 *
 * <h2>Why the body is empty</h2>
 * Because an {@code Application} subclass exists, the server scans the archive and registers every
 * {@code @Path} and {@code @Provider} class it finds by itself. Overriding {@code getClasses()}
 * would replace that scan with a hand-maintained list, and the first resource someone forgets to
 * add to it would 404 with nothing in the log to explain why.
 *
 * <h2>Why nothing is registered here</h2>
 * ADR-003 keeps the dependency list closed and the application portable across Jakarta EE 10
 * servers. Registering a {@code Feature} — a Jersey {@code MultiPartFeature}, a Jackson provider —
 * would bind the WAR to one server's implementation. JSON is handled by the platform's JSON-B,
 * multipart uploads by the Servlet API's {@code Part} (T-23), and both are available without
 * registering anything.
 *
 * <h2>Why there is no {@code <servlet>} for this in web.xml</h2>
 * The annotation is the declaration. A hand-written servlet mapping for the Jakarta REST servlet
 * would collide with the one the server derives from {@code @ApplicationPath} — T-17 acceptance
 * criterion 6 checks that neither is present.
 */
@ApplicationPath("/api")
public class JakartaRestApplication extends Application {
    // Intentionally empty — see the class documentation.
}
