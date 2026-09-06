package com.petlee.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Puts every resource class under {@code /api}. */
@ApplicationPath("/api")
public class JakartaRestApplication extends Application {
    // Empty on purpose: the server scans the archive for @Path and @Provider classes itself.
}
