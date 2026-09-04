package com.petlee.mapper;

import com.petlee.dto.CategoryDTO;
import com.petlee.model.Category;

import java.util.List;

/** {@link Category} to {@link CategoryDTO}. */
public final class CategoryMapper {

    private CategoryMapper() {
    }

    /**
     * @param category the category, may be {@code null}
     * @return the DTO, or {@code null} for a {@code null} argument
     */
    public static CategoryDTO toDto(Category category) {
        if (category == null) {
            return null;
        }
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getCategoryId());
        dto.setName(category.getCategoryName());
        return dto;
    }

    /**
     * @param categories the categories, may be {@code null}
     * @return the DTOs in the same order; an empty list for {@code null}, never {@code null}
     */
    public static List<CategoryDTO> toDtoList(List<Category> categories) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream().map(CategoryMapper::toDto).toList();
    }
}
