package com.petlee.web;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;

/**
 * The smallest possible proof that CDI is running and that EL can reach it.
 *
 * <p>{@code index.xhtml} renders {@code #{bootstrapCheck.cdiStatus}} beside a literal
 * {@code #{1+1}}. Together they separate three failures that otherwise look identical in a
 * browser — a blank page or raw {@code #{...}} text: Faces not mapped, EL not evaluating, and CDI
 * not active because {@code beans.xml} is missing or the class carries no bean-defining
 * annotation. T-17 criteria 4 and 5 are exactly these two expressions.
 *
 * <p>It is kept after T-17 because it costs nothing and stays useful: a deployment where this page
 * renders but a real bean does not points at that bean, not at the platform.
 *
 * <p>Per ADR-001 nothing in {@code com.petlee.web} may import {@code com.petlee.service} or
 * {@code com.petlee.repository}. This bean holds no state and calls nothing, so it cannot start.
 */
@Named("bootstrapCheck")
@RequestScoped
public class BootstrapCheckBean {

    /**
     * @return a fixed string. Its content is irrelevant; that it appears at all is the assertion.
     */
    public String getCdiStatus() {
        return "CDI is active";
    }
}
