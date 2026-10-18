package com.petlee.web.client;

import com.petlee.dto.CategoryDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.GenericType;

import java.util.List;

/** The category read endpoint, {@code GET /api/categories}. */
@ApplicationScoped
public class CategoryApi {

    private static final GenericType<List<CategoryDTO>> CATEGORY_LIST = new GenericType<>() { };

    @Inject
    private ApiClient api;

    /**
     * {@code GET /api/categories}.
     *
     * @return every category; empty, never null, when there are none
     */
    public List<CategoryDTO> findAll() {
        return api.get("/categories", CATEGORY_LIST);
    }
}
