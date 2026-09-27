package com.petlee.web.client;

import com.petlee.dto.AdminPetDTO;
import com.petlee.dto.CategoryDTO;
import com.petlee.dto.PetDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.GenericType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The moderation endpoints: {@code /api/admin}, plus the admin-only writes on
 * {@code /api/pets} and {@code /api/categories}. The API refuses every one of them with 403 for
 * a caller who is not an administrator; nothing here checks a role.
 */
@ApplicationScoped
public class AdminApi {

    private static final GenericType<List<AdminPetDTO>> LISTINGS = new GenericType<>() { };
    private static final GenericType<Map<Integer, Long>> COUNTS = new GenericType<>() { };

    @Inject
    private ApiClient api;

    /**
     * {@code GET /api/admin/pets}. A null filter is not sent, which the API reads as "any".
     *
     * @param categoryId the category to restrict to, or null
     * @param size       SMALL, MEDIUM or LARGE, or null
     * @param gender     MALE or FEMALE, or null
     * @return every matching listing, in every status, newest first
     */
    public List<AdminPetDTO> listings(Integer categoryId, String size, String gender) {
        // HashMap, not Map.of: the values may be null, and ApiClient skips those.
        Map<String, Object> query = new HashMap<>();
        query.put("categoryId", categoryId);
        query.put("size", size);
        query.put("gender", gender);
        return api.get("/admin/pets", query, LISTINGS);
    }

    /**
     * {@code PUT /api/admin/pets/{id}/status?status=}. REMOVED hides a listing, AVAILABLE puts
     * it back.
     *
     * @param id     the listing
     * @param status the new status
     * @return the listing in its new state
     */
    public PetDTO changeStatus(Long id, String status) {
        Map<String, Object> query = new HashMap<>();
        query.put("status", status);
        return api.put("/admin/pets/" + id + "/status", query, null, PetDTO.class);
    }

    /**
     * {@code DELETE /api/pets/{id}}, which an administrator may call on any listing.
     *
     * @param id the listing
     */
    public void deletePet(Long id) {
        api.delete("/pets/" + id);
    }

    /**
     * {@code GET /api/admin/category-counts}.
     *
     * @return listing counts keyed by category id; a category with no listings is absent
     */
    public Map<Integer, Long> categoryCounts() {
        return api.get("/admin/category-counts", COUNTS);
    }

    /**
     * {@code POST /api/categories}.
     *
     * @param name the new category's name
     * @return the created category
     * @throws ApiException 400 for a blank or over-long name, 409 for a duplicate
     */
    public CategoryDTO createCategory(String name) {
        return api.post("/categories", new CategoryDTO(null, name), CategoryDTO.class);
    }

    /**
     * {@code DELETE /api/categories/{id}}.
     *
     * @param id the category
     * @throws ApiException 409 if listings still use it
     */
    public void deleteCategory(Integer id) {
        api.delete("/categories/" + id);
    }
}
