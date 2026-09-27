package com.petlee.web.client;

import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.GenericType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The pet read endpoints under {@code /api/pets}. */
@ApplicationScoped
public class PetApi {

    private static final GenericType<List<PetDTO>> PET_LIST = new GenericType<>() { };

    @Inject
    private ApiClient api;

    /**
     * {@code GET /api/pets}. A null filter is not sent, which the API reads as "any".
     *
     * @param categoryId the category to restrict to, or null
     * @param size       SMALL, MEDIUM or LARGE, or null
     * @param gender     MALE or FEMALE, or null
     * @return the matching available pets, newest first
     * @throws ApiException 400 for an unrecognised size or gender
     */
    public List<PetDTO> gallery(Integer categoryId, String size, String gender) {
        // HashMap, not Map.of: the values may be null, and ApiClient skips those.
        Map<String, Object> query = new HashMap<>();
        query.put("categoryId", categoryId);
        query.put("size", size);
        query.put("gender", gender);
        return api.get("/pets", query, PET_LIST);
    }

    /**
     * {@code GET /api/pets/{id}}. The owner's contact fields are filled only when the API sees a
     * logged-in caller, that is, when {@link ApiCredentials} holds a live token.
     *
     * @param id the pet id
     * @return the pet in full
     * @throws ApiException 404 if there is no such pet
     */
    public PetDetailDTO detail(Long id) {
        return api.get("/pets/" + id, PetDetailDTO.class);
    }

    /**
     * {@code GET /api/pets/mine}.
     *
     * @return the caller's listings, newest first, in every status
     * @throws ApiException 401 if the caller is not logged in
     */
    public List<PetDTO> mine() {
        return api.get("/pets/mine", PET_LIST);
    }
}
