package com.petlee.web.bean;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;

/** Queues Faces messages for the page to render. Shared by the managed beans. */
final class Messages {

    private static final String LOGIN = "/login.xhtml?faces-redirect=true";

    private Messages() {
    }

    static void error(String text) { add(null, FacesMessage.SEVERITY_ERROR, text); }

    static void error(String clientId, String text) { add(clientId, FacesMessage.SEVERITY_ERROR, text); }

    static void warn(String text) { add(null, FacesMessage.SEVERITY_WARN, text); }

    static void info(String text) { add(null, FacesMessage.SEVERITY_INFO, text); }

    private static void add(String clientId, FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(clientId, new FacesMessage(severity, text, null));
    }

    /**
     * Keeps queued messages alive across a redirect.
     *
     * @param outcome the navigation outcome, returned unchanged
     * @return {@code outcome}
     */
    static String keep(String outcome) {
        FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(true);
        return outcome;
    }

    /**
     * For an API call answered 401: the token has expired and {@code ApiClient} has already
     * forgotten it, so the user is sent to log in again.
     *
     * @return the login page, keeping the message across the redirect
     */
    static String sessionExpired() {
        error("Your session has expired. Please log in again.");
        return keep(LOGIN);
    }
}
