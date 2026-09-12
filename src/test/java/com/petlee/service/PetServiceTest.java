package com.petlee.service;

import com.petlee.StubRepositories.StubCategoryRepository;
import com.petlee.StubRepositories.StubPetRepository;
import com.petlee.StubRepositories.StubUserRepository;
import com.petlee.dto.PetForm;
import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Specification §5's authorisation rules for a listing: only its owner or an administrator may
 * remove it, and only its owner may edit it. Exercised through {@code delete(...)} and
 * {@code update(Long, PetForm, Long)} — never the scalar overloads, which delegate through the
 * {@code self} proxy that is null under {@code new PetService(...)}.
 */
class PetServiceTest {

    private StubPetRepository pets;
    private StubUserRepository users;
    private User owner;
    private User otherUser;
    private Category category;
    private PetService service;

    @BeforeEach
    void setUp() {
        pets = new StubPetRepository();
        users = new StubUserRepository();
        StubCategoryRepository categories = new StubCategoryRepository();
        service = new PetService(pets, users, new CategoryService(categories), new ImageStore());

        owner = users.save(newUser("owner", "owner@example.com"));
        otherUser = users.save(newUser("other", "other@example.com"));

        category = new Category("Dogs");
        category.setCategoryId(1);
        categories.put(category);
    }

    private static User newUser(String username, String email) {
        User user = new User();
        user.setUserName(username);
        user.setFullName("Full Name");
        user.setEmail(email);
        return user;
    }

    private Pet newListing() {
        Pet pet = new Pet();
        pet.setPetName("Rex");
        pet.setSize(Pet.PetSize.MEDIUM);
        pet.setGender(Pet.PetGender.MALE);
        pet.setStatus(Pet.PetStatus.AVAILABLE);
        pet.setCategory(category);
        pet.setOwner(owner);
        return pets.save(pet);
    }

    @Test
    void aNonOwnerCannotDeleteSomeoneElsesListing() {
        Pet pet = newListing();

        AppException ex = assertThrows(AppException.class,
                () -> service.delete(pet.getPetId(), otherUser.getUserId(), false));

        assertEquals(403, ex.getStatus());
        assertTrue(pets.findById(pet.getPetId()).isPresent());
    }

    @Test
    void anAdministratorCanDeleteSomeoneElsesListing() {
        Pet pet = newListing();

        assertDoesNotThrow(() -> service.delete(pet.getPetId(), otherUser.getUserId(), true));

        assertTrue(pets.findById(pet.getPetId()).isEmpty());
    }

    @Test
    void theOwnerCanDeleteTheirOwnListing() {
        Pet pet = newListing();

        assertDoesNotThrow(() -> service.delete(pet.getPetId(), owner.getUserId(), false));

        assertTrue(pets.findById(pet.getPetId()).isEmpty());
    }

    @Test
    void aNonOwnerCannotUpdateSomeoneElsesListing() {
        Pet pet = newListing();
        PetForm form = new PetForm("Rex", "Labrador", 3, "MEDIUM", "MALE", "Friendly dog", null,
                category.getCategoryId());

        AppException ex = assertThrows(AppException.class,
                () -> service.update(pet.getPetId(), form, otherUser.getUserId()));

        assertEquals(403, ex.getStatus());
    }
}
