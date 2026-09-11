package com.petlee.it;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code api-contract.md} § CATEGORIES, plus the two administration endpoints T-34 added to the same
 * collection (ADR-002 #6).
 *
 * <p>Every category this suite creates, it deletes.
 */
class CategoryApiIT {

    private ApiTestClient guest;
    private ApiTestClient admin;

    @BeforeEach
    void openClients() {
        guest = new ApiTestClient();
        admin = new ApiTestClient();
        admin.login("admin", "Admin123!");
    }

    @AfterEach
    void closeClients() {
        guest.close();
        admin.close();
    }

    @Test
    @DisplayName("GET /api/categories — 200, an array of {id, name}, open to a guest")
    void listsTheVocabulary() {
        Response response = guest.get("/api/categories");

        assertEquals(200, response.getStatus());
        JsonArray categories = ApiTestClient.readArray(response);
        assertFalse(categories.isEmpty(), "seed.sql installs six");
        assertEquals(List.of("id", "name"), ApiTestClient.keysOf(categories.getJsonObject(0)));
    }

    @Test
    @DisplayName("POST /api/categories — 200 as an admin, and it appears in the vocabulary")
    void createsACategory() {
        String name = ApiTestClient.uniqueName("Cat");

        Response created = admin.post("/api/categories", Json.createObjectBuilder().add("name", name).build());

        assertEquals(200, created.getStatus());
        int id = ApiTestClient.readObject(created).getInt("id");
        try {
            assertTrue(ApiTestClient.readArray(guest.get("/api/categories")).stream()
                            .map(JsonObject.class::cast)
                            .anyMatch(category -> name.equals(category.getString("name"))),
                    "a created category must be visible to everyone");
        } finally {
            admin.delete("/api/categories/" + id);
        }
    }

    @Test
    @DisplayName("POST /api/categories — 409 CATEGORY_EXISTS for a duplicate name")
    void refusesADuplicateName() {
        Response response = admin.post("/api/categories",
                Json.createObjectBuilder().add("name", "Dogs").build());

        assertEquals(409, response.getStatus());
        assertEquals("CATEGORY_EXISTS", ApiTestClient.readObject(response).getString("code"));
    }

    @Test
    @DisplayName("DELETE /api/categories/{id} — 204 for one nothing references")
    void deletesAnUnusedCategory() {
        String name = ApiTestClient.uniqueName("Temp");
        int id = ApiTestClient.readObject(admin.post("/api/categories",
                Json.createObjectBuilder().add("name", name).build())).getInt("id");

        assertEquals(204, admin.delete("/api/categories/" + id).getStatus());
    }

    /**
     * The reason T-34 checks before deleting: {@code ON DELETE RESTRICT} would otherwise surface as
     * a 500. Specification §5 requires every pet to have a category, and this is the readable form
     * of that rule.
     */
    @Test
    @DisplayName("DELETE /api/categories/{id} — 409 CATEGORY_IN_USE, and JSON, not a 500")
    void refusesToDeleteACategoryHoldingListings() {
        int dogs = ApiTestClient.readArray(guest.get("/api/categories")).stream()
                .map(JsonObject.class::cast)
                .filter(category -> "Dogs".equals(category.getString("name")))
                .findFirst().orElseThrow().getInt("id");

        Response response = admin.delete("/api/categories/" + dogs);

        assertEquals(409, response.getStatus());
        assertTrue(response.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE));
        assertEquals("CATEGORY_IN_USE", ApiTestClient.readObject(response).getString("code"));
    }

    @Test
    @DisplayName("the write endpoints are 401 for a guest and 403 for a member")
    void writesAreAdminOnly() {
        JsonObject body = Json.createObjectBuilder().add("name", ApiTestClient.uniqueName("Nope")).build();

        assertEquals(401, guest.post("/api/categories", body).getStatus());
        assertEquals(401, guest.delete("/api/categories/1").getStatus());

        try (ApiTestClient member = new ApiTestClient()) {
            member.registerAndLogin(ApiTestClient.uniqueName("member"), "12345678");

            Response refused = member.post("/api/categories", body);
            assertEquals(403, refused.getStatus());
            assertEquals("NOT_ADMIN", ApiTestClient.readObject(refused).getString("code"));
            assertEquals(403, member.delete("/api/categories/1").getStatus());
        }
    }
}
