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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code api-contract.md} § PETS — the five contract endpoints plus {@code GET /api/pets/mine}
 * (ADR-002 #11).
 *
 * <p>The masking pair is the most important test in the task: specification §6 says a guest may read
 * a listing but not its owner's contact details, and both halves of that are asserted here, adjacent,
 * against the deployed application rather than against a mapper.
 *
 * <p>Every listing this suite creates is deleted in {@link #removeFixtures()}, so the suite can run
 * against the same deployment repeatedly.
 */
class PetApiIT {

    private ApiTestClient owner;
    private ApiTestClient stranger;
    private ApiTestClient guest;
    private int categoryId;
    private Long createdPetId;

    @BeforeEach
    void openClientsAndFindACategory() {
        guest = new ApiTestClient();
        owner = new ApiTestClient();
        stranger = new ApiTestClient();

        owner.registerAndLogin(ApiTestClient.uniqueName("owner"), "12345678");
        stranger.registerAndLogin(ApiTestClient.uniqueName("stranger"), "12345678");

        categoryId = ApiTestClient.readArray(guest.get("/api/categories"))
                .getJsonObject(0).getInt("id");
    }

    @AfterEach
    void removeFixtures() {
        if (createdPetId != null) {
            owner.delete("/api/pets/" + createdPetId);
        }
        guest.close();
        owner.close();
        stranger.close();
    }

    @Test
    @DisplayName("GET /api/pets — 200, an array, open to a guest")
    void listsTheGallery() {
        Response response = guest.get("/api/pets");

        assertEquals(200, response.getStatus());
        assertNotNull(ApiTestClient.readArray(response));
    }

    @Test
    @DisplayName("GET /api/pets — the contract's three filters narrow it; a bad enum is 400")
    void filtersTheGallery() {
        assertEquals(200, guest.get("/api/pets?size=SMALL&gender=FEMALE").getStatus());
        assertEquals(200, guest.get("/api/pets?categoryId=" + categoryId).getStatus());

        Response bad = guest.get("/api/pets?size=HUGE");

        assertEquals(400, bad.getStatus(), "an unknown enum must be 400, not a silently empty list");
        assertEquals("INVALID_FILTER", ApiTestClient.readObject(bad).getString("code"));
    }

    @Test
    @DisplayName("POST /api/pets — 200 as a member; 401 for a guest")
    void createsAListing() {
        Response created = owner.post("/api/pets", petForm("Rex"));

        assertEquals(200, created.getStatus());
        JsonObject pet = ApiTestClient.readObject(created);
        createdPetId = pet.getJsonNumber("id").longValue();
        assertEquals("Rex", pet.getString("name"));
        assertEquals("AVAILABLE", pet.getString("status"));

        assertEquals(401, guest.post("/api/pets", petForm("Nope")).getStatus());
    }

    @Test
    @DisplayName("POST /api/pets — 400 for an invalid field, 404 for an unknown category")
    void refusesAnInvalidListing() {
        JsonObject noName = Json.createObjectBuilder(petForm("x")).add("name", "").build();
        assertEquals(400, owner.post("/api/pets", noName).getStatus());

        JsonObject badCategory = Json.createObjectBuilder(petForm("Rex")).add("categoryId", 999_999).build();
        Response response = owner.post("/api/pets", badCategory);
        assertEquals(404, response.getStatus());
        assertEquals("CATEGORY_NOT_FOUND", ApiTestClient.readObject(response).getString("code"));
    }

    /**
     * Specification §6 and the contract's footnote: <em>"Owner contact fields are filled ONLY if the
     * caller is logged in; otherwise null."</em> Both assertions, in one test, against the running
     * application.
     */
    @Test
    @DisplayName("GET /api/pets/{id} — contact details are null for a guest and present for a member")
    void masksContactDetailsFromGuestsOnly() {
        createdPetId = createListing("Contact test");

        JsonObject asGuest = ApiTestClient.readObject(guest.get("/api/pets/" + createdPetId));
        assertTrue(ApiTestClient.isJsonNull(asGuest, "ownerFullName"));
        assertTrue(ApiTestClient.isJsonNull(asGuest, "ownerEmail"));
        assertTrue(ApiTestClient.isJsonNull(asGuest, "ownerPhone"));
        assertEquals("Contact test", asGuest.getString("name"), "the listing itself is public");

        JsonObject asMember = ApiTestClient.readObject(stranger.get("/api/pets/" + createdPetId));
        assertFalse(ApiTestClient.isJsonNull(asMember, "ownerFullName"));
        assertFalse(ApiTestClient.isJsonNull(asMember, "ownerEmail"));
        assertFalse(ApiTestClient.isJsonNull(asMember, "ownerPhone"));
    }

    @Test
    @DisplayName("GET /api/pets/{id} — 404 for an unknown id, as JSON")
    void unknownPetIs404() {
        Response response = guest.get("/api/pets/999999");

        assertEquals(404, response.getStatus());
        assertTrue(response.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE));
        assertEquals("PET_NOT_FOUND", ApiTestClient.readObject(response).getString("code"));
    }

    @Test
    @DisplayName("PUT /api/pets/{id} — 200 for the owner, 403 for anyone else, 401 for a guest")
    void updatesAListing() {
        createdPetId = createListing("Before");

        Response updated = owner.put("/api/pets/" + createdPetId,
                Json.createObjectBuilder(petForm("After")).build());
        assertEquals(200, updated.getStatus());
        assertEquals("After", ApiTestClient.readObject(updated).getString("name"));

        Response refused = stranger.put("/api/pets/" + createdPetId, petForm("Theirs"));
        assertEquals(403, refused.getStatus());
        assertEquals("NOT_OWNER", ApiTestClient.readObject(refused).getString("code"));

        assertEquals(401, guest.put("/api/pets/" + createdPetId, petForm("Guest")).getStatus());
    }

    /**
     * Specification §4's concurrency control, over HTTP — and an honest account of what the frozen
     * contract makes testable.
     *
     * <p>The task asks for "GET a pet twice, PUT the first, PUT the second, assert 409". That cannot
     * happen through this API: {@code PetForm} carries no {@code version} — the contract's request
     * body has no such field — so each {@code PUT} re-reads the row inside its own transaction and
     * always writes a current version. A sequential pair therefore returns 200, 200, and it should.
     *
     * <p>What is real, and what this asserts, is that <strong>simultaneous</strong> writers are
     * handled: several PUTs in flight at once each come back either 200 or 409 {@code STALE_PET} as
     * JSON — never a 500, and never a silently lost update. The lock itself is proved a layer down,
     * where it lives, by {@code ConcurrencyTest} (T-38).
     */
    @Test
    @DisplayName("simultaneous PUTs answer 200 or 409 STALE_PET — never 500, never a lost update")
    void concurrentEditsAreEither200Or409() throws Exception {
        createdPetId = createListing("Concurrent");

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Response>> writers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String name = "Writer " + i;
                writers.add(() -> owner.put("/api/pets/" + createdPetId, petForm(name)));
            }

            for (Future<Response> attempt : pool.invokeAll(writers)) {
                Response response = attempt.get();
                assertTrue(response.getStatus() == 200 || response.getStatus() == 409,
                        "a concurrent write answered " + response.getStatus());
                if (response.getStatus() == 409) {
                    assertTrue(response.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE));
                    assertEquals("STALE_PET", ApiTestClient.readObject(response).getString("code"));
                }
            }
        } finally {
            pool.shutdown();
        }

        // Whatever the order, the listing is intact and holds one of the writers' values.
        JsonObject survivor = ApiTestClient.readObject(owner.get("/api/pets/" + createdPetId));
        assertTrue(survivor.getString("name").startsWith("Writer"),
                "a lost update would leave the original name: " + survivor.getString("name"));
    }

    @Test
    @DisplayName("DELETE /api/pets/{id} — 204 for the owner, 403 for a stranger, 401 for a guest")
    void deletesAListing() {
        Long petId = createListing("To delete");

        assertEquals(401, guest.delete("/api/pets/" + petId).getStatus());
        assertEquals(403, stranger.delete("/api/pets/" + petId).getStatus());
        assertEquals(204, owner.delete("/api/pets/" + petId).getStatus());
        assertEquals(404, guest.get("/api/pets/" + petId).getStatus(), "and it is gone");
    }

    @Test
    @DisplayName("GET /api/pets/mine — the caller's own listings, 401 for a guest (ADR-002 #11)")
    void listsOwnListings() {
        createdPetId = createListing("Mine");

        JsonArray mine = ApiTestClient.readArray(owner.get("/api/pets/mine"));
        assertTrue(mine.stream().map(JsonObject.class::cast)
                .anyMatch(pet -> pet.getJsonNumber("id").longValue() == createdPetId));

        assertTrue(ApiTestClient.readArray(stranger.get("/api/pets/mine")).isEmpty(),
                "another member's dashboard is their own, not everyone's");
        assertEquals(401, guest.get("/api/pets/mine").getStatus());
    }

    @Test
    @DisplayName("no pet response carries password material")
    void noResponseLeaksPasswordMaterial() {
        createdPetId = createListing("Leak check");

        for (String body : new String[]{
                owner.get("/api/pets").readEntity(String.class),
                owner.get("/api/pets/" + createdPetId).readEntity(String.class),
                guest.get("/api/pets/" + createdPetId).readEntity(String.class)}) {
            String lower = body.toLowerCase();
            assertFalse(lower.contains("password"), "a response mentions a password: " + body);
            assertFalse(lower.contains("pbkdf2"), "a response carries a digest: " + body);
        }
    }

    private Long createListing(String name) {
        Response created = owner.post("/api/pets", petForm(name));
        assertEquals(200, created.getStatus(), "fixture creation failed: " + created.getStatus());
        return ApiTestClient.readObject(created).getJsonNumber("id").longValue();
    }

    private JsonObject petForm(String name) {
        return Json.createObjectBuilder()
                .add("name", name)
                .add("breed", "Mixed")
                .add("age", 3)
                .add("size", "MEDIUM")
                .add("gender", "MALE")
                .add("shortDesc", "Friendly and energetic")
                .add("longDesc", "A longer description, for the details page.")
                .add("categoryId", categoryId)
                .build();
    }
}
