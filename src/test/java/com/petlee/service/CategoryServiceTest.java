package com.petlee.service;

import com.petlee.dto.CategoryDTO;
import com.petlee.exception.ConflictException;
import com.petlee.exception.NotFoundException;
import com.petlee.exception.ValidationException;
import com.petlee.model.Category;
import com.petlee.model.Pet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-14's five acceptance criteria. */
class CategoryServiceTest {

    private FakeCategoryRepository categories;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        categories = new FakeCategoryRepository();
        service = new CategoryService(categories);
    }

    @Test
    @DisplayName("criterion 1: the seeded vocabulary is six categories, alphabetically")
    void listsTheSeededVocabularyInOrder() {
        List<CategoryDTO> all = service.findAll();

        assertEquals(6, all.size());
        assertEquals(List.of("Birds", "Cats", "Dogs", "Fish", "Reptiles", "Rodents"),
                all.stream().map(CategoryDTO::getName).toList());
    }

    @Test
    @DisplayName("criterion 2: a CategoryDTO carries exactly id and name")
    void exposesOnlyIdAndName() {
        assertEquals(List.of("id", "name"),
                Arrays.stream(CategoryDTO.class.getDeclaredFields())
                        .filter(f -> !f.isSynthetic())
                        .map(Field::getName)
                        .sorted()
                        .toList());
    }

    @Test
    @DisplayName("criterion 3: an unknown id is a 404 with code CATEGORY_NOT_FOUND")
    void rejectsAnUnknownId() {
        NotFoundException e = assertThrows(NotFoundException.class, () -> service.requireById(999));
        assertEquals("CATEGORY_NOT_FOUND", e.getCode());

        assertThrows(NotFoundException.class, () -> service.findById(999));
        assertThrows(NotFoundException.class, () -> service.requireById(null));
    }

    @Test
    @DisplayName("criterion 4: requireById hands back an entity a Pet can hold, with no second lookup")
    void returnsAnEntityReadyForAPet() {
        Category dogs = service.requireById(1);

        assertSame(categories.rows.get(0), dogs, "the already-loaded instance is returned as-is");

        Pet pet = new Pet();
        pet.setCategory(dogs);
        assertEquals("Dogs", pet.getCategory().getCategoryName());
    }

    @Test
    @DisplayName("criterion 5: requireById is package-private, so no REST resource can reach it")
    void keepsTheEntityDoorPackagePrivate() {
        Method requireById = Arrays.stream(CategoryService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("requireById"))
                .findFirst()
                .orElseThrow();

        int modifiers = requireById.getModifiers();
        assertFalse(Modifier.isPublic(modifiers), "requireById must not be public");
        assertFalse(Modifier.isProtected(modifiers), "protected would let a subclass republish it");

        // And nothing else on the class returns an entity.
        assertTrue(Arrays.stream(CategoryService.class.getMethods())
                        .filter(m -> m.getDeclaringClass() == CategoryService.class)
                        .noneMatch(m -> Category.class.isAssignableFrom(m.getReturnType())),
                "no public method of CategoryService may return a Category");
    }

    @Test
    @DisplayName("findById is the DTO-returning variant REST uses")
    void findsOneAsADto() {
        CategoryDTO dto = service.findById(3);

        assertEquals(3, dto.getId());
        assertEquals("Fish", dto.getName());
    }

    @Test
    @DisplayName("findAll is a stable order, not the insertion order")
    void ordersByNameNotByInsertion() {
        List<String> names = service.findAll().stream().map(CategoryDTO::getName).toList();
        assertEquals(names.stream().sorted(Comparator.naturalOrder()).toList(), names);
    }

    // ------------------------------------------------------------------- T-34's write methods

    @Test
    @DisplayName("T-34 criterion 4: a created category joins the vocabulary")
    void createsACategory() {
        CategoryDTO created = service.create("  Horses ");

        assertEquals("Horses", created.getName(), "the name is trimmed");
        assertTrue(service.findAll().stream().map(CategoryDTO::getName).anyMatch("Horses"::equals));
    }

    @Test
    @DisplayName("T-34 criterion 5: a duplicate name is 409 CATEGORY_EXISTS, whatever its case")
    void rejectsADuplicateName() {
        ConflictException e = assertThrows(ConflictException.class, () -> service.create("dOgS"));
        assertEquals("CATEGORY_EXISTS", e.getCode());
        assertEquals(6, service.findAll().size());
    }

    @Test
    @DisplayName("a blank name is 400, not a nameless row")
    void rejectsABlankName() {
        assertThrows(ValidationException.class, () -> service.create("   "));
        assertThrows(ValidationException.class, () -> service.create(null));
        assertThrows(ValidationException.class, () -> service.create("x".repeat(51)));
    }

    @Test
    @DisplayName("T-34 criterion 6: an unused category is deleted")
    void deletesAnUnusedCategory() {
        service.delete(6);

        assertEquals(5, service.findAll().size());
        assertThrows(NotFoundException.class, () -> service.findById(6));
    }

    @Test
    @DisplayName("T-34 criterion 7: a category holding pets is 409 CATEGORY_IN_USE, not 500")
    void refusesToDeleteACategoryInUse() {
        categories.petCounts.put(1, 3L);

        ConflictException e = assertThrows(ConflictException.class, () -> service.delete(1));
        assertEquals("CATEGORY_IN_USE", e.getCode());
        assertTrue(e.getMessage().contains("3"), "the message names the count");
        assertEquals(6, service.findAll().size());
    }

    @Test
    @DisplayName("deleting an unknown category is 404")
    void refusesToDeleteAnUnknownCategory() {
        assertThrows(NotFoundException.class, () -> service.delete(999));
    }
}
