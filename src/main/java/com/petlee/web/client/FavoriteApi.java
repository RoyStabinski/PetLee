package com.petlee.web.client;

import com.petlee.dto.PetDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.GenericType;

import java.util.List;

/** The caller's saved pets, under {@code /api/favorites}. Every call needs a logged-in caller. */
@ApplicationScoped
public class FavoriteApi {

    private static final GenericType<List<PetDTO>> PET_LIST = new GenericType<>() { };
    private static final GenericType<List<Long>> ID_LIST = new GenericType<>() { };

    @Inject
    private ApiClient api;

    /**
     * {@code GET /api/favorites}.
     *
     * @return the saved pets, newest saved first, adopted ones included
     * @throws ApiException 401 if the caller is not logged in
     */
    public List<PetDTO> list() {
        return api.get("/favorites", PET_LIST);
    }

    /**
     * {@code GET /api/favorites/ids}.
     *
     * @return the ids of the saved pets
     * @throws ApiException 401 if the caller is not logged in
     */
    public List<Long> ids() {
        return api.get("/favorites/ids", ID_LIST);
    }

    /**
     * {@code PUT /api/favorites/{petId}}. Idempotent: saving a saved pet succeeds.
     *
     * @param petId the pet to save
     * @throws ApiException 401 if not logged in, 404 if the pet is gone or no longer available
     */
    public void add(Long petId) {
        api.put("/favorites/" + petId, null, Void.class);
    }

    /**
     * {@code DELETE /api/favorites/{petId}}. Idempotent: removing an unsaved pet succeeds.
     *
     * @param petId the pet to forget
     * @throws ApiException 401 if not logged in
     */
    public void remove(Long petId) {
        api.delete("/favorites/" + petId);
    }
}
