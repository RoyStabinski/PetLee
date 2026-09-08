package com.petlee.service;

import com.petlee.model.Category;
import com.petlee.repository.CategoryRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory {@link CategoryRepository} for the service unit tests, seeded with the six
 * categories {@code seed.sql} installs.
 */
class FakeCategoryRepository extends CategoryRepository {

    final List<Category> rows = new ArrayList<>();

    FakeCategoryRepository() {
        // The seed vocabulary, in the order seed.sql inserts it — deliberately not alphabetical,
        // so findAllOrderedByName has something to order.
        add(1, "Dogs");
        add(2, "Cats");
        add(3, "Fish");
        add(4, "Rodents");
        add(5, "Birds");
        add(6, "Reptiles");
    }

    private void add(int id, String name) {
        Category category = new Category(name);
        category.setCategoryId(id);
        rows.add(category);
    }

    @Override
    public List<Category> findAllOrderedByName() {
        return rows.stream()
                .sorted(Comparator.comparing(Category::getCategoryName))
                .toList();
    }

    /** How many pets each category holds, as the tests choose to pretend. */
    final Map<Integer, Long> petCounts = new HashMap<>();

    private int nextId = 7;

    @Override
    public Category save(Category entity) {
        if (entity.getCategoryId() == null) {
            entity.setCategoryId(nextId++);
            rows.add(entity);
        }
        return entity;
    }

    @Override
    public void delete(Category entity) {
        rows.remove(entity);
    }

    @Override
    public boolean existsByName(String name) {
        return name != null && rows.stream()
                .anyMatch(c -> c.getCategoryName().equalsIgnoreCase(name));
    }

    @Override
    public long countPetsInCategory(Integer categoryId) {
        return petCounts.getOrDefault(categoryId, 0L);
    }

    @Override
    public Optional<Category> findById(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        return rows.stream().filter(c -> id.equals(c.getCategoryId())).findFirst();
    }
}
