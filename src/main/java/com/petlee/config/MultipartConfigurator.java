package com.petlee.config;

import com.petlee.rest.JakartaRestApplication;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.WebListener;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gives the Jakarta REST servlet its multipart limits, so {@code POST /api/pets/{id}/images} can
 * read {@code request.getPart("file")}.
 *
 * <h2>Why this is not an annotation, and not {@code web.xml}</h2>
 * The servlet that handles {@code /api/*} is the container's own, registered from
 * {@code @ApplicationPath} — there is no class of ours to put {@code @MultipartConfig} on. The
 * declarative alternative is a {@code <servlet>} element in {@code web.xml} naming that servlet
 * with no class and no mapping, purely to attach {@code <multipart-config>}. It works on Payara.
 * On WildFly it makes RESTEasy conclude the application is configured by hand, so it registers
 * nothing: the deployment starts without an error and every {@code /api} path answers 404. That is
 * the worst kind of failure — silent, and on one server only — so the configuration is applied
 * programmatically here, where both servers do the same thing.
 *
 * <p>Without it, {@code getPart} throws {@code IllegalStateException} and every upload is a 400,
 * whatever the file.
 *
 * <h2>Why the limits are here rather than checked in the resource</h2>
 * The container enforces them while reading the stream, so an oversized upload is refused before
 * it is buffered. T-16 checks the size again as it reads, because a cap the client can influence
 * is not a cap; this one is about not paying for the bytes in the first place.
 */
@WebListener
public class MultipartConfigurator implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(MultipartConfigurator.class.getName());

    /** 5 MB — the same limit {@code ImageStorageService} enforces on the bytes it reads. */
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** A little more, for the part headers and the {@code isMain} field around the file. */
    static final long MAX_REQUEST_BYTES = 6L * 1024 * 1024;

    /**
     * Zero: spool every part straight to the container's temporary directory instead of holding it
     * in the heap. A large upload should cost disk, not memory, and the file is streamed to T-16
     * either way.
     */
    static final int FILE_SIZE_THRESHOLD = 0;

    /** The Jakarta REST specification names the servlet after the {@code Application} subclass. */
    private static final String REST_SERVLET_NAME = JakartaRestApplication.class.getName();

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();

        ServletRegistration registration = context.getServletRegistration(REST_SERVLET_NAME);
        if (registration == null) {
            // Should a server name it differently, fall back to whichever servlet claims /api/*
            // rather than assuming the name and silently doing nothing.
            registration = byMapping(context);
        }

        if (!(registration instanceof ServletRegistration.Dynamic dynamic)) {
            LOGGER.log(Level.WARNING, () -> "Could not configure multipart on the Jakarta REST "
                    + "servlet; image uploads will be rejected with 400. Looked for "
                    + REST_SERVLET_NAME + " and for a servlet mapped to /api/*");
            return;
        }

        dynamic.setMultipartConfig(new MultipartConfigElement(
                null, MAX_FILE_BYTES, MAX_REQUEST_BYTES, FILE_SIZE_THRESHOLD));

        LOGGER.log(Level.INFO, () -> "Multipart configured on servlet '" + dynamic.getName()
                + "': maxFileSize=" + MAX_FILE_BYTES + ", maxRequestSize=" + MAX_REQUEST_BYTES);
    }

    private static ServletRegistration byMapping(ServletContext context) {
        for (Map.Entry<String, ? extends ServletRegistration> entry
                : context.getServletRegistrations().entrySet()) {
            if (entry.getValue().getMappings().stream().anyMatch(mapping -> mapping.startsWith("/api"))) {
                return entry.getValue();
            }
        }
        return null;
    }
}
