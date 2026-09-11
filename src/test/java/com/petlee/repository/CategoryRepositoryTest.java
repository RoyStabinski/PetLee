package com.petlee.repository;

import com.petlee.model.Category;
import com.petlee.model.User;
import com.petlee.test.DatabaseTest;
import com.petlee.test.TestData;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CategoryRepository} against the real database, including the two things only a database
 * enforces: the case-insensitive unique index, and {@code ON DELETE RESTRICT} on the pets' foreign
 * key (T-38 requirement 6).
 */
class CategoryRepositoryTest extends DatabaseTest {

    private CategoryRepository categories;

    @BeforeEach
    void injectRepository() {
        categories = inject(new CategoryRepository());
    }

    @Test
    @DisplayName("findAllOrderedByName sorts alphabetically, whatever order the rows went in")
    void ordersByName() {
        persist(TestData.aCategory().name("Rodents").build(),
                TestData.aCategory().name("Dogs").build(),
                TestData.aCategory().name("Cats").build());

        List<String> names = categories.findAllOrderedByName().stream()
                .map(Category::getCategoryName).toList();

        assertEquals(List.of("Cats", "Dogs", "Rodents"), names);
    }

    @Test
    @DisplayName("findByName and existsByName ignore case, as ux_category_name_lower does")
    void matchesNameCaseInsensitively() {
        persist(TestData.aCategory().name("Dogs").build());

        assertTrue(categories.findByName("dOgS").isPresent());
        assertTrue(categories.existsByName("DOGS"));
        assertFalse(categories.existsByName("Horses"));
        assertFalse(categories.existsByName(null));
    }

    @Test
    @DisplayName("a name that differs only in case is refused by the index, not merely by T-34")
    void duplicateNameIgnoringCaseIsRefused() {
        persist(TestData.aCategory().name("Dogs").build());

        Category clash = TestData.aCategory().name("dogs").build();

        assertThrows(PersistenceException.class, () -> inTransaction(manager -> manager.persist(clash)));
    }

    @Test
    @DisplayName("countPetsInCategory counts REMOVED listings too — they hold the key as well")
    void countsEveryStatus() {
        User owner = TestData.aUser().build();
        Category dogs = TestData.aCategory().name("Dogs").build();
        persist(owner, dogs);
        persist(TestData.aPet().owner(owner).category(dogs).build(),
                TestData.aPet().owner(owner).category(dogs)
                        .status(com.petlee.model.Pet.PetStatus.REMOVED).build());

        assertEquals(2L, categories.countPetsInCategory(dogs.getCategoryId()));
        assertEquals(0L, categories.countPetsInCategory(9999));
        assertEquals(0L, categories.countPetsInCategory(null));
    }

    /**
     * T-38 requirement 6, and the reason T-34 answers 409 {@code CATEGORY_IN_USE} rather than
     * letting this reach the caller as a 500. Specification §5 requires every pet to have a
     * category, and this is the constraint that makes that true.
     */
    @Test
    @DisplayName("ON DELETE RESTRICT: a category holding listings cannot be deleted")
    void categoryHoldingListingsCannotBeDeleted() {
        User owner = TestData.aUser().build();
        Category dogs = TestData.aCategory().name("Dogs").build();
        persist(owner, dogs);
        persist(TestData.aPet().owner(owner).category(dogs).build());

        assertThrows(PersistenceException.class,
                () -> inTransaction(manager -> categories.delete(dogs)));
    }

    @Test
    @DisplayName("an unused category deletes cleanly")
    void unusedCategoryDeletes() {
        Category horses = TestData.aCategory().name("Horses").build();
        persist(horses);

        inTransaction(manager -> categories.delete(horses));

        assertTrue(categories.findByName("Horses").isEmpty());
    }
}
