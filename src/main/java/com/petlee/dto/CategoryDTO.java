package com.petlee.dto;

import com.petlee.model.Category;

import java.io.Serializable;

/** A category as {@code GET /api/categories} returns it: {@code id}, {@code name}. */
public record CategoryDTO(Integer id, String name) implements Serializable {

    public static CategoryDTO of(Category c) {
        return new CategoryDTO(c.getCategoryId(), c.getCategoryName());
    }
}
