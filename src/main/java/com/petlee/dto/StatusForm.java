package com.petlee.dto;

/**
 * The one-field body of {@code PUT /api/admin/pets/{id}/status} — {@code {"status":"REMOVED"}}.
 *
 * <p>A whole object for one string, so that adding a moderation note later is a new field rather
 * than a new endpoint shape.
 */
public class StatusForm {

    private String status;

    public StatusForm() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
