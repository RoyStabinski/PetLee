package com.petlee.it;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The administration endpoints T-34 added — {@code GET /api/admin/pets} and
 * {@code PUT /api/admin/pets/{id}/status} (ADR-002 #6 and #12).
 *
 * <p>Deleting a listing is deliberately not here: it is {@code DELETE /api/pets/{id}}, tested by
 * {@code PetApiIT}, and an admin's use of it is the last test in this class.
 */
class AdminApiIT {

    private ApiTestClient admin;
    private ApiTestClient member;
    private ApiTestClient guest;
    private Long petId;

    @BeforeEach
    void createAListingAsAMember() {
        guest = new ApiTestClient();
        admin = new ApiTestClient();
        member = new ApiTestClient();

        admin.login("admin", "Admin123!");
        member.registerAndLogin(ApiTestClient.uniqueName("mem"), "12345678");

        int categoryId = ApiTestClient.readArray(guest.get("/api/categories")).getJsonObject(0).getInt("id");
        petId = ApiTestClient.readObject(member.post("/api/pets", Json.createObjectBuilder()
                .add("name", "Moderated " + ApiTestClient.runId())
                .add("breed", "Mixed")
                .add("age", 1)
                .add("size", "LARGE")
                .add("gender", "MALE")
                .add("shortDesc", "For the moderation tests")
                .add("categoryId", categoryId)
                .build())).getJsonNumber("id").longValue();
    }

    @AfterEach
    void removeFixtures() {
        member.delete("/api/pets/" + petId);
        guest.close();
        admin.close();
        member.close();
    }

    @Test
    @DisplayName("GET /api/admin/pets — 200 as an admin, with the moderation fields")
    void listsEveryListing() {
        Response response = admin.get("/api/admin/pets");

        assertEquals(200, response.getStatus());
        JsonObject mine = find(ApiTestClient.readArray(response), petId);
        assertTrue(mine.containsKey("ownerName"), "a moderator needs to know who posted it");
        assertTrue(mine.containsKey("createdAt"));
    }

    @Test
    @DisplayName("PUT status REMOVED hides a listing from the gallery but not from the admin table")
    void hidesAListing() {
        assertEquals(200, admin.put("/api/admin/pets/" + petId + "/status",
                Json.createObjectBuilder().add("status", "REMOVED").build()).getStatus());

        assertEquals("REMOVED", ApiTestClient.readObject(guest.get("/api/pets/" + petId)).getString("status"));
        assertFalse(contains(ApiTestClient.readArray(guest.get("/api/pets")), petId),
                "a hidden listing must leave the public gallery");
        assertTrue(contains(ApiTestClient.readArray(admin.get("/api/admin/pets")), petId),
                "and must stay in the moderation table");
    }

    @Test
    @DisplayName("PUT status AVAILABLE puts it back")
    void restoresAListing() {
        admin.put("/api/admin/pets/" + petId + "/status",
                Json.createObjectBuilder().add("status", "REMOVED").build());

        assertEquals(200, admin.put("/api/admin/pets/" + petId + "/status",
                Json.createObjectBuilder().add("status", "AVAILABLE").build()).getStatus());

        assertTrue(contains(ApiTestClient.readArray(guest.get("/api/pets")), petId));
    }

    @Test
    @DisplayName("PUT status — 400 for any status but REMOVED or AVAILABLE, 404 for an unknown pet")
    void refusesAnyOtherStatus() {
        Response badStatus = admin.put("/api/admin/pets/" + petId + "/status",
                Json.createObjectBuilder().add("status", "ADOPTED").build());
        assertEquals(400, badStatus.getStatus());
        assertEquals("INVALID_STATUS", ApiTestClient.readObject(badStatus).getString("code"));

        assertEquals(404, admin.put("/api/admin/pets/999999/status",
                Json.createObjectBuilder().add("status", "REMOVED").build()).getStatus());
    }

    @Test
    @DisplayName("every admin endpoint is 401 for a guest and 403 NOT_ADMIN for a member")
    void adminEndpointsAreAdminOnly() {
        JsonObject status = Json.createObjectBuilder().add("status", "REMOVED").build();

        assertEquals(401, guest.get("/api/admin/pets").getStatus());
        assertEquals(401, guest.put("/api/admin/pets/" + petId + "/status", status).getStatus());

        Response refusedList = member.get("/api/admin/pets");
        assertEquals(403, refusedList.getStatus());
        assertEquals("NOT_ADMIN", ApiTestClient.readObject(refusedList).getString("code"));
        assertEquals(403, member.put("/api/admin/pets/" + petId + "/status", status).getStatus());
    }

    /**
     * The existing endpoint, unchanged: T-15 has always allowed the owner <em>or</em> an admin, and
     * T-34 deliberately did not add a second deletion path.
     */
    @Test
    @DisplayName("an admin may delete another member's listing through DELETE /api/pets/{id}")
    void adminMayDeleteSomeoneElsesListing() {
        assertEquals(204, admin.delete("/api/pets/" + petId).getStatus());
        assertEquals(404, guest.get("/api/pets/" + petId).getStatus());
    }

    @Test
    @DisplayName("no admin response carries password material, even though it names owners")
    void noResponseLeaksPasswordMaterial() {
        String body = admin.get("/api/admin/pets").readEntity(String.class).toLowerCase();

        assertFalse(body.contains("password"));
        assertFalse(body.contains("pbkdf2"));
    }

    private static JsonObject find(JsonArray pets, Long id) {
        return pets.stream().map(JsonObject.class::cast)
                .filter(pet -> pet.getJsonNumber("id").longValue() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("pet " + id + " is not in the response"));
    }

    private static boolean contains(JsonArray pets, Long id) {
        return pets.stream().map(JsonObject.class::cast)
                .anyMatch(pet -> pet.getJsonNumber("id").longValue() == id);
    }
}
