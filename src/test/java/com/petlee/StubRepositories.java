package com.petlee;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.User;
import com.petlee.repository.CategoryRepository;
import com.petlee.repository.PetRepository;
import com.petlee.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hand-written in-memory stand-ins for the JPA repositories, used by the unit tests in this
 * module. ADR-003 forbids Mockito, so each nested class subclasses the real repository and
 * overrides every method the tests exercise. The parent's {@code @PersistenceContext EntityManager}
 * field stays {@code null} under {@code new StubXRepository()} — that is safe only because every
 * overridden method below is self-contained and never reads it.
 */
public final class StubRepositories {

    private StubRepositories() {
    }

    public static final class StubUserRepository extends UserRepository {
        private final Map<Long, User> byId = new HashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<User> findById(Long id) {
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return byId.values().stream()
                    .filter(u -> u.getUserName().equals(username))
                    .findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return username != null && byId.values().stream()
                    .anyMatch(u -> u.getUserName().equals(username));
        }

        // Case-insensitive, matching the real repository's LOWER(email) = LOWER(:email).
        @Override
        public boolean existsByEmail(String email) {
            return email != null && byId.values().stream()
                    .anyMatch(u -> u.getEmail().equalsIgnoreCase(email));
        }

        @Override
        public User save(User user) {
            if (user.getUserId() == null) {
                user.setUserId(ids.incrementAndGet());
            }
            byId.put(user.getUserId(), user);
            return user;
        }
    }

    public static final class StubPetRepository extends PetRepository {
        private final Map<Long, Pet> byId = new HashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<Pet> findById(Long id) {
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
        }

        @Override
        public Pet save(Pet pet) {
            if (pet.getPetId() == null) {
                pet.setPetId(ids.incrementAndGet());
            }
            byId.put(pet.getPetId(), pet);
            return pet;
        }

        @Override
        public void delete(Pet pet) {
            byId.remove(pet.getPetId());
        }
    }

    public static final class StubCategoryRepository extends CategoryRepository {
        private final Map<Integer, Category> byId = new HashMap<>();

        /** Test setup helper: seeds a category that already carries its id. */
        public void put(Category category) {
            byId.put(category.getCategoryId(), category);
        }

        @Override
        public Optional<Category> findById(Integer id) {
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
        }
    }
}
