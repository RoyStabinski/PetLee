package com.petlee.it;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /api/pets/{id}/images} and the deletion T-23 added (ADR-002 #10).
 *
 * <p>The multipart body is assembled by hand in {@link ApiTestClient#upload} — the same way
 * {@code ApiClient} builds it in production, because ADR-003 rules out a multipart library on either
 * side.
 */
class ImageApiIT {

    /** A one-pixel PNG, shared with the other suites. */
    private static final byte[] PNG = ApiTestClient.onePixelPng();

    private ApiTestClient owner;
    private ApiTestClient stranger;
    private ApiTestClient guest;
    private Long petId;

    @BeforeEach
    void createAListing() {
        guest = new ApiTestClient();
        owner = new ApiTestClient();
        stranger = new ApiTestClient();
        owner.registerAndLogin(ApiTestClient.uniqueName("own"), "12345678");
        stranger.registerAndLogin(ApiTestClient.uniqueName("str"), "12345678");

        int categoryId = ApiTestClient.readArray(guest.get("/api/categories")).getJsonObject(0).getInt("id");
        petId = ApiTestClient.readObject(owner.post("/api/pets", Json.createObjectBuilder()
                .add("name", "Photographed")
                .add("breed", "Mixed")
                .add("age", 2)
                .add("size", "SMALL")
                .add("gender", "FEMALE")
                .add("shortDesc", "Has photographs")
                .add("categoryId", categoryId)
                .build())).getJsonNumber("id").longValue();
    }

    @AfterEach
    void removeFixtures() {
        owner.delete("/api/pets/" + petId);
        guest.close();
        owner.close();
        stranger.close();
    }

    @Test
    @DisplayName("POST /api/pets/{id}/images — 200 and a PetImageDTO for the owner")
    void uploadsAPhotograph() {
        Response response = owner.upload("/api/pets/" + petId + "/images", PNG, "rex.png", true);

        assertEquals(200, response.getStatus());
        JsonObject image = ApiTestClient.readObject(response);
        assertEquals(List.of("id", "imageUrl", "isMain"), ApiTestClient.keysOf(image));
        assertTrue(image.getBoolean("isMain"));
        assertTrue(image.getString("imageUrl").startsWith("/images/"));
    }

    @Test
    @DisplayName("the uploaded photograph becomes the listing's thumbnail")
    void uploadedImageAppearsOnTheListing() {
        owner.upload("/api/pets/" + petId + "/images", PNG, "rex.png", true);

        JsonObject pet = ApiTestClient.readObject(guest.get("/api/pets/" + petId));

        assertEquals(1, pet.getJsonArray("images").size());
    }

    @Test
    @DisplayName("upload — 401 for a guest and 403 for someone who does not own the listing")
    void uploadIsOwnerOnly() {
        assertEquals(401, guest.upload("/api/pets/" + petId + "/images", PNG, "rex.png", false).getStatus());

        Response refused = stranger.upload("/api/pets/" + petId + "/images", PNG, "rex.png", false);
        assertEquals(403, refused.getStatus());
    }

    @Test
    @DisplayName("upload — 400 for a file type that is not an image")
    void refusesAnUnsupportedFileType() {
        Response response = owner.upload("/api/pets/" + petId + "/images",
                "not a photograph".getBytes(java.nio.charset.StandardCharsets.UTF_8), "notes.txt", false);

        assertEquals(400, response.getStatus());
    }

    @Test
    @DisplayName("upload — 404 for a listing that does not exist")
    void refusesAnUnknownListing() {
        assertEquals(404, owner.upload("/api/pets/999999/images", PNG, "rex.png", false).getStatus());
    }

    @Test
    @DisplayName("DELETE /api/pets/{petId}/images/{imageId} — 204 for the owner, 403 for a stranger")
    void deletesAPhotograph() {
        int imageId = ApiTestClient.readObject(
                owner.upload("/api/pets/" + petId + "/images", PNG, "rex.png", true)).getInt("id");

        assertEquals(403, stranger.delete("/api/pets/" + petId + "/images/" + imageId).getStatus());
        assertEquals(204, owner.delete("/api/pets/" + petId + "/images/" + imageId).getStatus());
        assertEquals(0, ApiTestClient.readObject(guest.get("/api/pets/" + petId))
                .getJsonArray("images").size());
    }
}
