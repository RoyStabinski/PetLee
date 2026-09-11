package com.petlee.it;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frozen key sets of {@code api-contract.md}, asserted against the deployed application.
 *
 * <p>Every expectation below is copied from the contract's own example bodies. The assertions are
 * <strong>exact</strong>, not "contains": a missing key breaks a client, and an <em>extra</em> key
 * is how a password leaks. Both have to fail here, which is why this suite compares whole key sets
 * rather than reading fields.
 */
class ContractShapeIT {

    /** UserDTO — the contract's note: "never includes password". */
    private static final List<String> USER_KEYS =
            List.of("email", "fullName", "id", "phone", "role", "username");

    /** PetDTO — the gallery view, main image only. */
    private static final List<String> PET_KEYS =
            List.of("age", "categoryName", "gender", "id", "mainImageUrl", "name", "shortDesc",
                    "size", "status");

    /** PetDetailDTO — everything, including the three owner fields a guest receives as null. */
    private static final List<String> PET_DETAIL_KEYS =
            List.of("age", "breed", "categoryName", "gender", "id", "images", "longDesc", "name",
                    "ownerEmail", "ownerFullName", "ownerPhone", "shortDesc", "size", "status");

    private static final List<String> CATEGORY_KEYS = List.of("id", "name");

    private static final List<String> IMAGE_KEYS = List.of("id", "imageUrl", "isMain");

    /** AdminPetDTO — PetDTO plus the two moderation fields (ADR-002 #12). */
    private static final List<String> ADMIN_PET_KEYS =
            List.of("age", "categoryName", "createdAt", "gender", "id", "mainImageUrl", "name",
                    "ownerName", "shortDesc", "size", "status");

    private ApiTestClient guest;
    private ApiTestClient member;
    private ApiTestClient admin;
    private String username;
    private Long petId;

    @BeforeEach
    void createOneOfEverything() {
        guest = new ApiTestClient();
        member = new ApiTestClient();
        admin = new ApiTestClient();

        username = ApiTestClient.uniqueName("shp");
        member.registerAndLogin(username, "12345678");
        admin.login("admin", "Admin123!");

        int categoryId = ApiTestClient.readArray(guest.get("/api/categories")).getJsonObject(0).getInt("id");
        petId = ApiTestClient.readObject(member.post("/api/pets", Json.createObjectBuilder()
                .add("name", "Shape check")
                .add("breed", "Mixed")
                .add("age", 4)
                .add("size", "MEDIUM")
                .add("gender", "FEMALE")
                .add("shortDesc", "For the contract shape tests")
                .add("longDesc", "Longer text.")
                .add("categoryId", categoryId)
                .build())).getJsonNumber("id").longValue();
    }

    @AfterEach
    void removeFixtures() {
        member.delete("/api/pets/" + petId);
        guest.close();
        member.close();
        admin.close();
    }

    @Test
    @DisplayName("UserDTO has exactly the contract's six keys, from register and from login alike")
    void userDtoShape() {
        JsonObject registered = ApiTestClient.readObject(guest.post("/api/users/register",
                ApiTestClient.registration(ApiTestClient.uniqueName("shp"), "12345678")));
        JsonObject loggedIn = ApiTestClient.readObject(member.login(username, "12345678"));

        assertEquals(USER_KEYS, ApiTestClient.keysOf(registered));
        assertEquals(USER_KEYS, ApiTestClient.keysOf(loggedIn));
        assertFalse(registered.toString().toLowerCase().contains("password"));
    }

    @Test
    @DisplayName("PetDTO — the gallery shape — has exactly the contract's nine keys")
    void petDtoShape() {
        JsonArray gallery = ApiTestClient.readArray(guest.get("/api/pets"));

        assertTrue(gallery.size() > 0, "the fixture listing should be in the gallery");
        assertEquals(PET_KEYS, ApiTestClient.keysOf(gallery.getJsonObject(0)));
    }

    @Test
    @DisplayName("PetDetailDTO has the same keys for a guest and a member — only the values differ")
    void petDetailDtoShape() {
        assertEquals(PET_DETAIL_KEYS,
                ApiTestClient.keysOf(ApiTestClient.readObject(guest.get("/api/pets/" + petId))),
                "a guest gets every key, with the contact fields null — not a shorter object");
        assertEquals(PET_DETAIL_KEYS,
                ApiTestClient.keysOf(ApiTestClient.readObject(member.get("/api/pets/" + petId))));
    }

    @Test
    @DisplayName("CategoryDTO has exactly id and name")
    void categoryDtoShape() {
        assertEquals(CATEGORY_KEYS, ApiTestClient.keysOf(
                ApiTestClient.readArray(guest.get("/api/categories")).getJsonObject(0)));
    }

    @Test
    @DisplayName("PetImageDTO has exactly id, imageUrl and isMain")
    void petImageDtoShape() {
        JsonObject image = ApiTestClient.readObject(member.upload("/api/pets/" + petId + "/images",
                ApiTestClient.onePixelPng(), "shape.png", true));

        assertEquals(IMAGE_KEYS, ApiTestClient.keysOf(image));
    }

    @Test
    @DisplayName("AdminPetDTO is PetDTO plus ownerName and createdAt, and nothing else")
    void adminPetDtoShape() {
        JsonArray all = ApiTestClient.readArray(admin.get("/api/admin/pets"));

        assertTrue(all.size() > 0);
        assertEquals(ADMIN_PET_KEYS, ApiTestClient.keysOf(all.getJsonObject(0)));
    }

    @Test
    @DisplayName("an error body has exactly code and message — no field, no detail, no stack trace")
    void errorShape() {
        assertEquals(List.of("code", "message"),
                ApiTestClient.keysOf(ApiTestClient.readObject(guest.get("/api/pets/999999"))));
    }
}
