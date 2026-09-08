package com.petlee.web.bean;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/** Queues Faces messages for the page to render. Shared by the managed beans. */
final class Messages {

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
     * Picks one violation message, for a form that shows a single error at a time.
     *
     * @param invalid  the validation failure
     * @param fallback the message to use when the set is empty
     * @return the first message the set yields, or {@code fallback}
     */
    static String firstViolation(ConstraintViolationException invalid, String fallback) {
        for (ConstraintViolation<?> violation : invalid.getConstraintViolations()) {
            return violation.getMessage();
        }
        return fallback;
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
}
