package com.petlee.repository;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.User;
import com.petlee.test.CountingDriver;
import com.petlee.test.DatabaseTest;
import com.petlee.test.TestData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PetRepository} against the real database — T-38 requirement 3, the largest suite, because
 * T-08 carries the most logic.
 *
 * <p>Three of these tests defend rules that live in the query and nowhere else: the gallery is
 * {@code AVAILABLE}-only and newest-first, {@code findDetailById} fetches a graph that survives its
 * {@code EntityManager}, and the gallery costs a bounded number of statements. Each one fails if
 * the corresponding clause is removed from T-08.
 */
class PetRepositoryTest extends DatabaseTest {

    private PetRepository pets;
    private User owner;
    private User someoneElse;
    private Category dogs;
    private Category cats;

    @BeforeEach
    void injectRepositoryAndSeedTheVocabulary() {
        pets = inject(new PetRepository());

        owner = TestData.aUser().username("owner").email("owner@example.com").build();
        someoneElse = TestData.aUser().username("other").email("other@example.com").build();
        dogs = TestData.aCategory().name("Dogs").build();
        cats = TestData.aCategory().name("Cats").build();
        persist(owner, someoneElse, dogs, cats);
    }

    @AfterEach
    void stopCounting() {
        CountingDriver.stopRecording();
    }

    @Test
    @DisplayName("the gallery is AVAILABLE only, newest first")
    void galleryIsAvailableOnlyNewestFirst() {
        Pet oldest = savePet(TestData.aPet().owner(owner).category(dogs).name("Oldest").build());
        Pet newest = savePet(TestData.aPet().owner(owner).category(dogs).name("Newest").build());
        Pet adopted = savePet(TestData.aPet().owner(owner).category(dogs).name("Adopted")
                .status(Pet.PetStatus.ADOPTED).build());
        Pet removed = savePet(TestData.aPet().owner(owner).category(dogs).name("Removed")
                .status(Pet.PetStatus.REMOVED).build());

        // Explicit timestamps: two inserts in the same millisecond would make the order a coin toss,
        // and a passing coin toss proves nothing.
        backdate(oldest.getPetId(), LocalDateTime.of(2026, 1, 1, 9, 0));
        backdate(newest.getPetId(), LocalDateTime.of(2026, 6, 1, 9, 0));
        backdate(adopted.getPetId(), LocalDateTime.of(2026, 7, 1, 9, 0));
        backdate(removed.getPetId(), LocalDateTime.of(2026, 8, 1, 9, 0));

        List<String> names = pets.findByFilter(PetFilter.none()).stream()
                .map(Pet::getPetName).toList();

        assertEquals(List.of("Newest", "Oldest"), names);
    }

    @Test
    @DisplayName("each filter alone selects the right subset")
    void eachFilterAlone() {
        savePet(TestData.aPet().owner(owner).category(dogs).name("Big male dog")
                .size(Pet.PetSize.LARGE).gender(Pet.PetGender.MALE).build());
        savePet(TestData.aPet().owner(owner).category(cats).name("Small female cat")
                .size(Pet.PetSize.SMALL).gender(Pet.PetGender.FEMALE).build());

        assertEquals(List.of("Big male dog"), names(PetFilter.builder()
                .categoryId(dogs.getCategoryId()).build()));
        assertEquals(List.of("Small female cat"), names(PetFilter.builder()
                .size(Pet.PetSize.SMALL).build()));
        assertEquals(List.of("Small female cat"), names(PetFilter.builder()
                .gender(Pet.PetGender.FEMALE).build()));
    }

    @Test
    @DisplayName("all three filters together select the intersection, and nothing when it is empty")
    void allThreeFiltersCombined() {
        savePet(TestData.aPet().owner(owner).category(dogs).name("Wanted")
                .size(Pet.PetSize.LARGE).gender(Pet.PetGender.MALE).build());
        savePet(TestData.aPet().owner(owner).category(dogs).name("Wrong size")
                .size(Pet.PetSize.SMALL).gender(Pet.PetGender.MALE).build());
        savePet(TestData.aPet().owner(owner).category(cats).name("Wrong category")
                .size(Pet.PetSize.LARGE).gender(Pet.PetGender.MALE).build());

        assertEquals(List.of("Wanted"), names(PetFilter.builder()
                .categoryId(dogs.getCategoryId())
                .size(Pet.PetSize.LARGE)
                .gender(Pet.PetGender.MALE)
                .build()));

        assertEquals(List.of(), names(PetFilter.builder()
                .categoryId(cats.getCategoryId())
                .size(Pet.PetSize.SMALL)
                .gender(Pet.PetGender.FEMALE)
                .build()));
    }

    /**
     * The regression test for the details page's lazy associations, and mandatory.
     *
     * <p>It asserts on <strong>what the query fetched</strong>, not on whether reading it later
     * happens to work. A first draft closed the {@code EntityManager} and read the graph, and it
     * passed with the fetch joins deleted: EclipseLink resolves an untouched collection through the
     * still-open factory, and its lazy {@code @ManyToOne}s are not lazy at all without weaving. So
     * the test would have gone green while production — where the persistence context ends with the
     * request — threw {@code LazyInitializationException} on Hibernate.
     *
     * <p>Two assertions instead, both of which fail the moment the joins go: the graph is
     * {@code isLoaded} the instant the query returns, and reading all of it costs no further
     * statement.
     */
    @Test
    @DisplayName("findDetailById fetches the whole graph in one statement, before anyone reads it")
    void detailGraphIsFetchedNotLazyLoaded() {
        Pet stored = savePet(TestData.aPet().owner(owner).category(dogs).name("Rex").build());
        TestData.aPetImage().pet(stored).main(true).build();
        inTransaction(manager -> manager.merge(stored));

        EntityManager separate = freshEntityManager();
        try {
            PetRepository repository = injectInto(new PetRepository(), separate);

            CountingDriver.startRecording();
            Pet loaded = repository.findDetailById(stored.getPetId()).orElseThrow();
            int afterTheQuery = CountingDriver.recorded().size();

            PersistenceUnitUtil loadState = separate.getEntityManagerFactory().getPersistenceUnitUtil();
            assertTrue(loadState.isLoaded(loaded, "images"),
                    "images must arrive with the pet; the details page reads them after the request");
            assertTrue(loadState.isLoaded(loaded, "category"), "category must arrive with the pet");
            assertTrue(loadState.isLoaded(loaded, "owner"), "owner must arrive with the pet");

            assertEquals("Dogs", loaded.getCategory().getCategoryName());
            assertEquals("owner", loaded.getOwner().getUserName());
            assertEquals(1, loaded.getImages().size());

            assertEquals(afterTheQuery, CountingDriver.recorded().size(),
                    "reading the graph must cost nothing: " + CountingDriver.recorded());
            assertEquals(1, afterTheQuery, "and the graph must arrive in one statement");
        } finally {
            CountingDriver.stopRecording();
            separate.close();
        }
    }

    /** The other half: the graph survives the manager that loaded it. */
    @Test
    @DisplayName("… so the graph is still readable after its EntityManager is closed")
    void detailGraphSurvivesTheEntityManager() {
        Pet stored = savePet(TestData.aPet().owner(owner).category(dogs).name("Rex").build());
        TestData.aPetImage().pet(stored).main(true).build();
        inTransaction(manager -> manager.merge(stored));

        Pet detached;
        EntityManager separate = freshEntityManager();
        try {
            detached = injectInto(new PetRepository(), separate)
                    .findDetailById(stored.getPetId()).orElseThrow();
        } finally {
            separate.close();
        }

        assertEquals("Dogs", detached.getCategory().getCategoryName());
        assertEquals("owner", detached.getOwner().getUserName());
        assertEquals(1, detached.getImages().size());
    }

    @Test
    @DisplayName("an unknown id is Optional.empty, not an exception")
    void unknownIdIsEmpty() {
        assertTrue(pets.findDetailById(999_999L).isEmpty());
        assertTrue(pets.findDetailById(null).isEmpty());
    }

    @Test
    @DisplayName("findByOwnerId includes REMOVED listings and excludes everyone else's")
    void ownerListingIncludesRemoved() {
        savePet(TestData.aPet().owner(owner).category(dogs).name("Mine available").build());
        savePet(TestData.aPet().owner(owner).category(dogs).name("Mine removed")
                .status(Pet.PetStatus.REMOVED).build());
        savePet(TestData.aPet().owner(someoneElse).category(dogs).name("Theirs").build());

        List<String> mine = pets.findByOwnerId(owner.getUserId()).stream()
                .map(Pet::getPetName).toList();

        assertEquals(2, mine.size());
        assertTrue(mine.contains("Mine removed"), "an owner must see what they withdrew");
        assertTrue(pets.findByOwnerId(someoneElse.getUserId()).size() == 1);
    }

    @Test
    @DisplayName("findAllForAdmin returns every status")
    void adminListingReturnsEveryStatus() {
        savePet(TestData.aPet().owner(owner).category(dogs).build());
        savePet(TestData.aPet().owner(owner).category(dogs).status(Pet.PetStatus.ADOPTED).build());
        savePet(TestData.aPet().owner(someoneElse).category(cats).status(Pet.PetStatus.REMOVED).build());

        assertEquals(3, pets.findAllForAdmin(PetFilter.none()).size());
        assertEquals(1, pets.findByFilter(PetFilter.none()).size(), "the gallery still hides two");
    }

    /**
     * T-38 requirement 4. Twenty listings with two photographs each: without the fetch joins this
     * is one query for the pets and forty more for their categories and images, and nothing but
     * production notices. The bound is three, and the failure message carries the statements so the
     * next person can see which one multiplied.
     */
    @Test
    @DisplayName("the gallery costs a bounded number of statements — no N+1")
    void galleryDoesNotIssueOneQueryPerPet() {
        for (int i = 0; i < 20; i++) {
            Pet pet = savePet(TestData.aPet().owner(owner).category(dogs).name("Pet " + i).build());
            TestData.aPetImage().pet(pet).main(true).build();
            TestData.aPetImage().pet(pet).main(false).build();
            inTransaction(manager -> manager.merge(pet));
        }
        em.clear();

        CountingDriver.startRecording();
        List<Pet> gallery = pets.findByFilter(PetFilter.none());
        // Touch what the gallery renders, so a lazy association would have to load here if it could.
        gallery.forEach(pet -> {
            assertNotNull(pet.getCategory().getCategoryName());
            pet.getImages().forEach(image -> assertNotNull(image.getImageUrl()));
        });
        List<String> statements = CountingDriver.recorded();

        assertEquals(20, gallery.size());
        assertTrue(statements.size() <= 3,
                "the gallery issued " + statements.size() + " statements: " + statements);
    }

    private List<String> names(PetFilter filter) {
        return pets.findByFilter(filter).stream().map(Pet::getPetName).toList();
    }

    private Pet savePet(Pet pet) {
        persist(pet);
        return pet;
    }

    /** {@link DatabaseTest#inject} against a manager other than this test's. */
    private static <R> R injectInto(R repository, EntityManager manager) {
        try {
            java.lang.reflect.Field field = AbstractRepository.class.getDeclaredField("em");
            field.setAccessible(true);
            field.set(repository, manager);
            return repository;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
