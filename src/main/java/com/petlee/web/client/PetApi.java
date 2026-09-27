package com.petlee.web.client;

import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.GenericType;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The pet endpoints under {@code /api/pets}. */
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

    /**
     * {@code POST /api/pets}. The owner is whoever the token belongs to.
     *
     * @param form the new listing
     * @return the created listing
     * @throws ApiException 400 for an invalid form, 401 if the caller is not logged in
     */
    public PetDTO create(PetForm form) {
        return api.post("/pets", form, PetDTO.class);
    }

    /**
     * {@code PUT /api/pets/{id}?version=}.
     *
     * @param id      the listing
     * @param form    the new values
     * @param version the {@code version} from {@link #detail}; null is sent as absent
     * @return the updated listing
     * @throws ApiException 400 for an invalid form, 403 if not the owner, 404 if gone, 409 if
     *                      the version is stale or missing
     */
    public PetDTO update(Long id, PetForm form, Long version) {
        // HashMap, not Map.of: the version may be null, and ApiClient skips it then.
        Map<String, Object> query = new HashMap<>();
        query.put("version", version);
        return api.put("/pets/" + id, query, form, PetDTO.class);
    }

    /**
     * {@code DELETE /api/pets/{id}}.
     *
     * @param id the listing
     * @throws ApiException 403 if neither owner nor admin, 404 if gone, 409 on a concurrent edit
     */
    public void delete(Long id) {
        api.delete("/pets/" + id);
    }

    /**
     * {@code POST /api/pets/{id}/image}, as the multipart part {@code file}. The stream is read
     * during the call but not closed.
     *
     * @param id          the listing
     * @param content     the photograph's bytes
     * @param fileName    the file name to send, may be null
     * @param contentType its media type, such as {@code image/png}
     * @return the listing, with its new {@code imageUrl}
     * @throws ApiException 400 if missing, too large or not a supported image, 403 if not the owner
     */
    public PetDTO uploadImage(Long id, InputStream content, String fileName, String contentType) {
        EntityPart part;
        try {
            EntityPart.Builder builder = EntityPart.withName("file").content(content)
                    .mediaType(contentType == null ? "application/octet-stream" : contentType);
            if (fileName != null && !fileName.isBlank()) {
                builder.fileName(fileName);
            }
            part = builder.build();
        } catch (IOException | IllegalArgumentException unbuildable) {
            // IllegalArgumentException: the browser sent a content type that does not parse.
            throw new ApiException(400, "UPLOAD_FAILED", "The photo could not be read.", unbuildable);
        }
        return api.postMultipart("/pets/" + id + "/image", List.of(part), PetDTO.class);
    }
}
