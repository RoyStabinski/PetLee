package com.petlee.repository;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.PetImage;
import com.petlee.model.User;
import com.petlee.test.DatabaseTest;
import com.petlee.test.TestData;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PetImageRepository} against the real database — T-38 requirement 5, plus the partial unique
 * index from requirement 6.
 *
 * <p>{@code ux_pet_image_main} is a unique index on {@code (pet_id) WHERE is_main}, which is the one
 * constraint in this schema that no in-memory database models faithfully — and the reason T-16
 * clears the flag before setting it rather than the other way round.
 */
class PetImageRepositoryTest extends DatabaseTest {

    private PetImageRepository images;
    private Pet pet;
    private Pet otherPet;

    @BeforeEach
    void injectRepositoryAndSeedAPet() {
        images = inject(new PetImageRepository());

        User owner = TestData.aUser().build();
        Category dogs = TestData.aCategory().name("Dogs").build();
        persist(owner, dogs);

        pet = TestData.aPet().owner(owner).category(dogs).name("Rex").build();
        otherPet = TestData.aPet().owner(owner).category(dogs).name("Bella").build();
        persist(pet, otherPet);
    }

    @Test
    @DisplayName("findByPetId lists the main photograph first, and only this pet's")
    void listsMainFirst() {
        store(image(pet, "/images/second.jpg", false));
        store(image(pet, "/images/main.jpg", true));
        store(image(otherPet, "/images/someone-elses.jpg", true));

        List<String> urls = images.findByPetId(pet.getPetId()).stream()
                .map(PetImage::getImageUrl).toList();

        assertEquals(List.of("/images/main.jpg", "/images/second.jpg"), urls);
    }

    @Test
    @DisplayName("findMainByPetId is empty for a pet with photographs but none flagged main")
    void mainIsEmptyWhenNoneIsFlagged() {
        store(image(pet, "/images/one.jpg", false));

        assertTrue(images.findMainByPetId(pet.getPetId()).isEmpty());
        assertTrue(images.findMainByPetId(999_999L).isEmpty());
    }

    @Test
    @DisplayName("countByPetId counts this pet's photographs and nobody else's")
    void countsPerPet() {
        store(image(pet, "/images/one.jpg", true));
        store(image(pet, "/images/two.jpg", false));
        store(image(otherPet, "/images/three.jpg", true));

        assertEquals(2L, images.countByPetId(pet.getPetId()));
        assertEquals(1L, images.countByPetId(otherPet.getPetId()));
        assertEquals(0L, images.countByPetId(999_999L));
    }

    /**
     * T-16's order of operations, and the index is why it is that order: clear, flush, then set.
     * Doing it the other way round means two rows are momentarily main, and the database refuses.
     */
    @Test
    @DisplayName("clearMainFlag then setting a new main succeeds, and leaves exactly one main")
    void clearThenSetMain() {
        PetImage first = store(image(pet, "/images/first.jpg", true));
        PetImage second = store(image(pet, "/images/second.jpg", false));

        inTransaction(manager -> {
            images.clearMainFlag(pet.getPetId());
            manager.flush();
            PetImage promoted = manager.find(PetImage.class, second.getImageId());
            promoted.setIsMain(true);
        });
        em.clear();

        assertEquals("/images/second.jpg",
                images.findMainByPetId(pet.getPetId()).orElseThrow().getImageUrl());
        assertEquals(1L, images.findByPetId(pet.getPetId()).stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsMain())).count());
        assertEquals(2L, images.countByPetId(pet.getPetId()));
        assertTrue(images.findByPetId(pet.getPetId()).stream()
                        .anyMatch(image -> image.getImageId().equals(first.getImageId())),
                "demoting the old main must not delete it");
    }

    @Test
    @DisplayName("ux_pet_image_main: a second main photograph for the same pet is refused")
    void twoMainImagesAreRefused() {
        store(image(pet, "/images/first.jpg", true));

        PetImage secondMain = image(pet, "/images/second.jpg", true);

        assertThrows(PersistenceException.class,
                () -> inTransaction(manager -> manager.persist(secondMain)));
    }

    @Test
    @DisplayName("… but two different pets may each have one, which is what 'partial' means")
    void twoPetsMayEachHaveAMain() {
        store(image(pet, "/images/rex.jpg", true));
        store(image(otherPet, "/images/bella.jpg", true));

        assertTrue(images.findMainByPetId(pet.getPetId()).isPresent());
        assertTrue(images.findMainByPetId(otherPet.getPetId()).isPresent());
    }

    @Test
    @DisplayName("deleting the pet takes its photographs with it — ON DELETE CASCADE")
    void deletingThePetRemovesItsImages() {
        store(image(pet, "/images/one.jpg", true));
        store(image(pet, "/images/two.jpg", false));
        store(image(otherPet, "/images/bella.jpg", true));

        inTransaction(manager -> manager.remove(manager.find(Pet.class, pet.getPetId())));
        em.clear();

        assertEquals(0L, images.countByPetId(pet.getPetId()));
        assertEquals(1L, images.countByPetId(otherPet.getPetId()),
                "the cascade takes this pet's photographs and nobody else's");
    }

    private static PetImage image(Pet pet, String url, boolean main) {
        return TestData.aPetImage().pet(pet).url(url).main(main).build();
    }

    private PetImage store(PetImage image) {
        inTransaction(manager -> manager.persist(image));
        return image;
    }
}
