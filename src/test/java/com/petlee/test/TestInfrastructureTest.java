package com.petlee.test;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.User;
import com.petlee.service.CategoryService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-36's own acceptance criteria: the infrastructure the next three tasks are built on, proving
 * itself before anything depends on it.
 *
 * <p>The two {@code sameUsername} tests are the point of criterion 3 — they insert the identical
 * row and both pass, in either order, which is only true if every test really does start from an
 * empty database.
 */
class TestInfrastructureTest extends DatabaseTest {

    @Test
    @DisplayName("criterion 2: every test method starts against an empty database")
    void startsEmpty() {
        assertEquals(0L, count("users"));
        assertEquals(0L, count("category"));
        assertEquals(0L, count("pet"));
        assertEquals(0L, count("pet_image"));
    }

    @Test
    @DisplayName("criterion 3a: this test and the next insert the same username, and both pass")
    void sameUsernameFirst() {
        persist(TestData.aUser().username("collision").email("collision@example.com").build());

        assertEquals(1L, count("users"));
    }

    @Test
    @DisplayName("criterion 3b: … in either order, because neither sees the other's rows")
    void sameUsernameSecond() {
        persist(TestData.aUser().username("collision").email("collision@example.com").build());

        assertEquals(1L, count("users"));
    }

    @Test
    @DisplayName("criterion 4: aPet().build() is persistable as it stands")
    void buildsAPersistablePet() {
        User owner = TestData.aUser().build();
        Category category = TestData.aCategory().build();
        Pet pet = TestData.aPet().owner(owner).category(category).build();

        persist(owner, category, pet);

        assertNotNull(pet.getPetId(), "the database assigned an id");
        assertNotNull(pet.getCreatedAt(), "@PrePersist stamped created_at");
        assertEquals(Pet.PetStatus.AVAILABLE, pet.getStatus());
        // Not "equals 0": EclipseLink stamps the first version as 1 where Hibernate uses 0, and
        // the number is the provider's business. What matters to T-04 and T-15 is that the column
        // is populated, so an update has something to compare against.
        assertNotNull(pet.getVersion(), "@Version is populated on insert");
    }

    @Test
    @DisplayName("backdate rewrites created_at, which @PrePersist would otherwise own")
    void backdatesAListing() {
        User owner = TestData.aUser().build();
        Category category = TestData.aCategory().build();
        Pet pet = TestData.aPet().owner(owner).category(category).build();
        persist(owner, category, pet);

        backdate(pet.getPetId(), java.time.LocalDateTime.of(2020, 1, 1, 9, 0));

        Pet reloaded = em.find(Pet.class, pet.getPetId());
        assertEquals(java.time.LocalDateTime.of(2020, 1, 1, 9, 0), reloaded.getCreatedAt());
    }

    @Test
    @DisplayName("criterion 5: a service runs on a fake repository with no database at all")
    void servicesRunOnFakes() {
        // Nothing in this test touches the EntityManager the base class opened: the fake IS the
        // repository, injected through the constructor T-36 requirement 8 asks every service for.
        CategoryService service = new CategoryService(new Fakes.Categories());

        assertEquals(6, service.findAll().size());
        assertTrue(service.findAll().stream().anyMatch(c -> "Dogs".equals(c.getName())));
    }

    private long count(String table) {
        Number rows = (Number) em.createNativeQuery("SELECT count(*) FROM " + table).getSingleResult();
        return rows.longValue();
    }
}
