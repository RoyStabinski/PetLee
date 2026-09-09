package com.petlee.test;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.PetImage;
import com.petlee.model.User;
import com.petlee.repository.CategoryRepository;
import com.petlee.repository.PetFilter;
import com.petlee.repository.PetImageRepository;
import com.petlee.repository.PetRepository;
import com.petlee.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The hand-written repository doubles the service unit tests run on.
 *
 * <p>Hand-written rather than mocked: ADR-003 closes the dependency list, so there is no Mockito —
 * and for four repositories with narrow interfaces a fake is about as much code as the mock setup
 * would have been, and reads better in the test. Each one subclasses the real repository and
 * overrides every method it uses, so the inherited {@code EntityManager} is never touched and no
 * database is involved.
 *
 * <p>They live here, in one file, so that T-37's service tests and anything written later share a
 * single set. A double that exists twice is a double that eventually disagrees with itself.
 *
 * <pre>
 * Fakes.Users users = new Fakes.Users();
 * UserService service = new UserService(users, new PasswordHasher());
 * </pre>
 *
 * <p>Their fields are public on purpose: a test asserts on what reached the repository
 * ({@code users.last()}, {@code pets.findAllForAdminCalls}) and stages what it should do next
 * ({@code pets.failNextSaveWith}). That is the whole interface of a test double.
 *
 * <p>What they do <strong>not</strong> reproduce is anything the database enforces — the unique
 * indexes, {@code ON DELETE RESTRICT}, real optimistic locking. Those are T-38's, against a real
 * PostgreSQL, on {@link DatabaseTest}.
 */
public final class Fakes {

    private Fakes() {
    }

    /**
     * An in-memory {@link UserRepository} for the service unit tests.
     *
     * <p>Hand-written rather than mocked: ADR-003 closes the dependency list, so there is no Mockito.
     * Subclassing works because every repository method is public and non-final, and the inherited
     * {@code EntityManager} is never touched — each method used here is overridden.
     *
     * <p>Uniqueness mirrors T-06 and the schema: username compared exactly, email compared
     * case-insensitively ({@code ux_users_email_lower}).
     */
    public static final class Users extends UserRepository {

        /** Every user "stored", in insertion order. */
        public final List<User> saved = new ArrayList<>();

        /** When set, the next {@link #save(User)} throws it — the concurrent-registration race. */
        public RuntimeException failNextSaveWith;

        private long nextId = 1L;

        @Override
        public Optional<User> findByUsername(String username) {
            if (username == null) {
                return Optional.empty();
            }
            return saved.stream().filter(u -> username.equals(u.getUserName())).findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            if (email == null) {
                return Optional.empty();
            }
            return saved.stream().filter(u -> equalsIgnoringCase(u.getEmail(), email)).findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public boolean existsByEmail(String email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public Optional<User> findById(Long id) {
            if (id == null) {
                return Optional.empty();
            }
            return saved.stream().filter(u -> id.equals(u.getUserId())).findFirst();
        }

        @Override
        public User save(User entity) {
            if (failNextSaveWith != null) {
                RuntimeException failure = failNextSaveWith;
                failNextSaveWith = null;
                throw failure;
            }
            if (entity.getUserId() == null) {
                entity.setUserId(nextId++);
                saved.add(entity);
            }
            return entity;
        }

        /** The most recently saved entity — the tests inspect the digest that reached it. */
        public User last() {
            return saved.get(saved.size() - 1);
        }

        private static boolean equalsIgnoringCase(String a, String b) {
            return a != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * An in-memory {@link CategoryRepository} for the service unit tests, seeded with the six
     * categories {@code seed.sql} installs.
     */
    public static final class Categories extends CategoryRepository {

        public final List<Category> rows = new ArrayList<>();

        public Categories() {
            // The seed vocabulary, in the order seed.sql inserts it — deliberately not alphabetical,
            // so findAllOrderedByName has something to order.
            add(1, "Dogs");
            add(2, "Cats");
            add(3, "Fish");
            add(4, "Rodents");
            add(5, "Birds");
            add(6, "Reptiles");
        }

        private void add(int id, String name) {
            Category category = new Category(name);
            category.setCategoryId(id);
            rows.add(category);
        }

        @Override
        public List<Category> findAllOrderedByName() {
            return rows.stream()
                    .sorted(Comparator.comparing(Category::getCategoryName))
                    .toList();
        }

        /** How many pets each category holds, as the tests choose to pretend. */
        public final Map<Integer, Long> petCounts = new HashMap<>();

        private int nextId = 7;

        @Override
        public Category save(Category entity) {
            if (entity.getCategoryId() == null) {
                entity.setCategoryId(nextId++);
                rows.add(entity);
            }
            return entity;
        }

        @Override
        public void delete(Category entity) {
            rows.remove(entity);
        }

        @Override
        public boolean existsByName(String name) {
            return name != null && rows.stream()
                    .anyMatch(c -> c.getCategoryName().equalsIgnoreCase(name));
        }

        @Override
        public long countPetsInCategory(Integer categoryId) {
            return petCounts.getOrDefault(categoryId, 0L);
        }

        @Override
        public Optional<Category> findById(Integer id) {
            if (id == null) {
                return Optional.empty();
            }
            return rows.stream().filter(c -> id.equals(c.getCategoryId())).findFirst();
        }
    }

    /**
     * An in-memory {@link PetRepository} for the service unit tests.
     *
     * <p>The three listing methods reproduce the status and ordering rules T-08's Javadoc states, so
     * that a service test can tell {@code findByFilter} (the public gallery) apart from
     * {@code findAllForAdmin} (everything) — those rules live in the query, and the only thing
     * {@code PetService} can be held responsible for is calling the right one. The proof that the real
     * SQL behaves this way is T-38's integration test against a live database.
     */
    public static final class Pets extends PetRepository {

        public final List<Pet> rows = new ArrayList<>();

        /** When set, the next {@link #save(Pet)} throws it — the concurrent-edit case. */
        public RuntimeException failNextSaveWith;

        /** Counts calls, so a test can assert which listing query the service chose. */
        public int findByFilterCalls;
        public int findAllForAdminCalls;

        private long nextId = 10L;

        /**
         * Stands in for the provider's clock. Each persist gets a later timestamp than the last, so
         * "newest first" is decidable in a test rather than depending on two {@code now()} calls
         * landing in different nanoseconds.
         */
        private LocalDateTime clock = LocalDateTime.of(2026, 1, 1, 12, 0);

        @Override
        public List<Pet> findByFilter(PetFilter filter) {
            findByFilterCalls++;
            return matching(filter).stream()
                    .filter(p -> p.getStatus() == Pet.PetStatus.AVAILABLE)
                    .toList();
        }

        @Override
        public List<Pet> findAllForAdmin(PetFilter filter) {
            findAllForAdminCalls++;
            return matching(filter);
        }

        @Override
        public List<Pet> findByOwnerId(Long ownerId) {
            if (ownerId == null) {
                return List.of();
            }
            return newestFirst(rows.stream()
                    .filter(p -> p.getOwner() != null && ownerId.equals(p.getOwner().getUserId()))
                    .toList());
        }

        @Override
        public Optional<Pet> findDetailById(Long id) {
            if (id == null) {
                return Optional.empty();
            }
            return rows.stream().filter(p -> id.equals(p.getPetId())).findFirst();
        }

        @Override
        public Optional<Pet> findById(Long id) {
            return findDetailById(id);
        }

        @Override
        public Pet save(Pet entity) {
            if (failNextSaveWith != null) {
                RuntimeException failure = failNextSaveWith;
                failNextSaveWith = null;
                throw failure;
            }
            if (entity.getPetId() == null) {
                entity.setPetId(nextId++);
                prePersist(entity);
                rows.add(entity);
            }
            return entity;
        }

        /**
         * What {@code Pet.onCreated()} does when the provider fires {@code @PrePersist}: stamp
         * {@code createdAt} and default the status. Neither field has a setter — the provider owns
         * them — so the fake reaches the field the same way the provider does.
         */
        private void prePersist(Pet entity) {
            clock = clock.plusMinutes(1);
            try {
                Field createdAt = Pet.class.getDeclaredField("createdAt");
                createdAt.setAccessible(true);
                createdAt.set(entity, clock);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Pet.createdAt is no longer a field of that name", e);
            }
            if (entity.getStatus() == null) {
                entity.setStatus(Pet.PetStatus.AVAILABLE);
            }
        }

        @Override
        public void delete(Pet entity) {
            // The row goes, and its images with it — cascade = ALL with orphanRemoval on Pet.images,
            // plus ON DELETE CASCADE in the schema. The list is cleared so a test can observe it.
            entity.getImages().clear();
            rows.remove(entity);
        }

        /** The most recently saved pet. */
        public Pet last() {
            return rows.get(rows.size() - 1);
        }

        private List<Pet> matching(PetFilter filter) {
            PetFilter criteria = filter != null ? filter : PetFilter.none();
            return newestFirst(rows.stream()
                    .filter(p -> criteria.getCategoryId() == null
                            || (p.getCategory() != null
                                && criteria.getCategoryId().equals(p.getCategory().getCategoryId())))
                    .filter(p -> criteria.getSize() == null || criteria.getSize() == p.getSize())
                    .filter(p -> criteria.getGender() == null || criteria.getGender() == p.getGender())
                    .toList());
        }

        /** created_at DESC, as specification §11 requires and T-08 implements. */
        private static List<Pet> newestFirst(List<Pet> unordered) {
            return unordered.stream()
                    .sorted(Comparator.comparing(Pet::getCreatedAt).reversed())
                    .toList();
        }
    }

    /**
     * An in-memory {@link PetImageRepository} for the T-16 unit tests.
     *
     * <p>It reproduces the two behaviours the service depends on and that live in the query rather
     * than in Java: {@code findByPetId} orders main-first then oldest-first, and
     * {@code clearMainFlag} is a bulk update over one pet's rows. The database's part — the partial
     * unique index {@code ux_pet_image_main} — is not simulated; what the tests here can prove is that
     * the service never asks the database to hold two main rows at once.
     */
    public static final class Images extends PetImageRepository {

        public final List<PetImage> rows = new ArrayList<>();

        /** When set, the next {@link #save(PetImage)} throws it — the failed-insert case. */
        public RuntimeException failNextSaveWith;

        private int nextId = 100;
        private LocalDateTime clock = LocalDateTime.of(2026, 1, 1, 12, 0);

        @Override
        public List<PetImage> findByPetId(Long petId) {
            if (petId == null) {
                return List.of();
            }
            return rows.stream()
                    .filter(i -> i.getPet() != null && petId.equals(i.getPet().getPetId()))
                    .sorted(Comparator.comparing((PetImage i) -> Boolean.TRUE.equals(i.getIsMain())).reversed()
                            .thenComparing(PetImage::getUpdatedAt))
                    .toList();
        }

        @Override
        public Optional<PetImage> findMainByPetId(Long petId) {
            return findByPetId(petId).stream().filter(i -> Boolean.TRUE.equals(i.getIsMain())).findFirst();
        }

        @Override
        public void clearMainFlag(Long petId) {
            findByPetId(petId).forEach(i -> i.setIsMain(false));
        }

        @Override
        public long countByPetId(Long petId) {
            return findByPetId(petId).size();
        }

        @Override
        public Optional<PetImage> findById(Integer id) {
            if (id == null) {
                return Optional.empty();
            }
            return rows.stream().filter(i -> id.equals(i.getImageId())).findFirst();
        }

        @Override
        public PetImage save(PetImage entity) {
            if (failNextSaveWith != null) {
                RuntimeException failure = failNextSaveWith;
                failNextSaveWith = null;
                throw failure;
            }
            stampUpdatedAt(entity);
            if (entity.getImageId() == null) {
                entity.setImageId(nextId++);
                rows.add(entity);
            }
            return entity;
        }

        @Override
        public void delete(PetImage entity) {
            rows.remove(entity);
        }

        /** What {@code PetImage.onUpsert()} does when the provider fires {@code @PrePersist}. */
        private void stampUpdatedAt(PetImage entity) {
            clock = clock.plusMinutes(1);
            entity.setUpdatedAt(clock);
        }
    }
}
