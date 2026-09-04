package com.petlee.dto;

/** A category as {@code GET /api/categories} returns it: {@code id}, {@code name}. */
public class CategoryDTO {

    private Integer id;
    private String name;

    public CategoryDTO() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
