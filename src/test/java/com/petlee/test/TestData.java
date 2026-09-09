package com.petlee.test;

import com.petlee.model.Category;
import com.petlee.model.Pet;
import com.petlee.model.PetImage;
import com.petlee.model.User;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fluent builders for the four entities, with defaults that already satisfy every {@code NOT NULL}
 * column and constraint in {@code schema.sql}.
 *
 * <pre>
 * User owner = TestData.aUser().build();
 * Pet rex = TestData.aPet().owner(owner).category(dogs).name("Rex").size(Pet.PetSize.LARGE).build();
 * </pre>
 *
 * <p>Every builder produces something persistable as it stands, so a test says only what it cares
 * about. Hand-built entity graphs repeated in thirty tests are where a suite goes to die: the day a
 * column becomes {@code NOT NULL}, every one of them has to be found and edited.
 *
 * <h2>Unique-by-default</h2>
 * Usernames, emails and category names carry a counter, because {@code users} and {@code category}
 * have unique indexes and two tests building "a user" must not collide. Where a test cares about the
 * value — the duplicate-registration tests do — it sets it explicitly.
 */
public final class TestData {

    /** Makes the defaults unique within a JVM. Shared by every builder. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private TestData() {
    }

    /** @return a builder for an ordinary member */
    public static UserBuilder aUser() {
        return new UserBuilder(User.Role.USER);
    }

    /** @return a builder for an administrator — the role, and nothing else, differs */
    public static UserBuilder anAdmin() {
        return new UserBuilder(User.Role.ADMIN);
    }

    /** @return a builder for a category */
    public static CategoryBuilder aCategory() {
        return new CategoryBuilder();
    }

    /**
     * @return a builder for a listing. An owner and a category are required and have no default:
     *         both are {@code NOT NULL} foreign keys, and specification §5 gives every listing
     *         exactly one owner and one predefined category.
     */
    public static PetBuilder aPet() {
        return new PetBuilder();
    }

    /** @return a builder for a photograph. The pet is required. */
    public static PetImageBuilder aPetImage() {
        return new PetImageBuilder();
    }

    private static int next() {
        return SEQUENCE.incrementAndGet();
    }

    /** Builds a {@link User}. */
    public static final class UserBuilder {

        private final User user = new User();

        private UserBuilder(User.Role role) {
            int n = next();
            user.setUserName("user" + n);
            // A PBKDF2 digest in shape, not a plaintext password: nothing in a test fixture should
            // look like a credential that works.
            user.setPassword("pbkdf2_sha256$210000$c2FsdA==$ZGlnZXN0");
            user.setFullName("Test User " + n);
            user.setEmail("user" + n + "@example.com");
            user.setPhoneNumber("050-000" + n);
            user.setRegion("Tel Aviv");
            user.setRole(role);
        }

        public UserBuilder username(String username) {
            user.setUserName(username);
            return this;
        }

        public UserBuilder password(String password) {
            user.setPassword(password);
            return this;
        }

        public UserBuilder fullName(String fullName) {
            user.setFullName(fullName);
            return this;
        }

        public UserBuilder email(String email) {
            user.setEmail(email);
            return this;
        }

        public UserBuilder phone(String phone) {
            user.setPhoneNumber(phone);
            return this;
        }

        public UserBuilder region(String region) {
            user.setRegion(region);
            return this;
        }

        public UserBuilder role(User.Role role) {
            user.setRole(role);
            return this;
        }

        public User build() {
            return user;
        }
    }

    /** Builds a {@link Category}. */
    public static final class CategoryBuilder {

        private final Category category = new Category("Category " + next());

        public CategoryBuilder name(String name) {
            category.setCategoryName(name);
            return this;
        }

        public Category build() {
            return category;
        }
    }

    /** Builds a {@link Pet}. */
    public static final class PetBuilder {

        private final Pet pet = new Pet();

        private PetBuilder() {
            int n = next();
            pet.setPetName("Pet " + n);
            pet.setBreed("Mixed");
            pet.setAge(3);
            pet.setSize(Pet.PetSize.MEDIUM);
            pet.setGender(Pet.PetGender.MALE);
            pet.setShortDesc("Friendly and energetic");
            pet.setLongDesc("A longer description, for the details page.");
            pet.setStatus(Pet.PetStatus.AVAILABLE);
        }

        public PetBuilder name(String name) {
            pet.setPetName(name);
            return this;
        }

        public PetBuilder breed(String breed) {
            pet.setBreed(breed);
            return this;
        }

        public PetBuilder age(Integer age) {
            pet.setAge(age);
            return this;
        }

        public PetBuilder size(Pet.PetSize size) {
            pet.setSize(size);
            return this;
        }

        public PetBuilder gender(Pet.PetGender gender) {
            pet.setGender(gender);
            return this;
        }

        public PetBuilder shortDesc(String shortDesc) {
            pet.setShortDesc(shortDesc);
            return this;
        }

        public PetBuilder longDesc(String longDesc) {
            pet.setLongDesc(longDesc);
            return this;
        }

        public PetBuilder status(Pet.PetStatus status) {
            pet.setStatus(status);
            return this;
        }

        public PetBuilder owner(User owner) {
            pet.setOwner(owner);
            return this;
        }

        public PetBuilder category(Category category) {
            pet.setCategory(category);
            return this;
        }

        /**
         * @return the listing
         * @throws IllegalStateException if no owner or no category was given — both are
         *         {@code NOT NULL}, and the message says so here rather than as a constraint
         *         violation three frames deep
         */
        public Pet build() {
            if (pet.getOwner() == null) {
                throw new IllegalStateException("aPet() needs an owner: .owner(aUser().build())");
            }
            if (pet.getCategory() == null) {
                throw new IllegalStateException("aPet() needs a category: .category(aCategory().build())");
            }
            return pet;
        }
    }

    /** Builds a {@link PetImage}. */
    public static final class PetImageBuilder {

        private final PetImage image = new PetImage();

        private PetImageBuilder() {
            image.setImageUrl("/images/photo-" + next() + ".jpg");
            image.setIsMain(Boolean.FALSE);
        }

        public PetImageBuilder url(String url) {
            image.setImageUrl(url);
            return this;
        }

        public PetImageBuilder main(boolean isMain) {
            image.setIsMain(isMain);
            return this;
        }

        /** Sets both sides of the association, so the pet's list is usable without a reload. */
        public PetImageBuilder pet(Pet pet) {
            image.setPet(pet);
            if (pet != null && pet.getImages() != null && !pet.getImages().contains(image)) {
                pet.getImages().add(image);
            }
            return this;
        }

        public PetImage build() {
            if (image.getPet() == null) {
                throw new IllegalStateException("aPetImage() needs a pet: .pet(...)");
            }
            return image;
        }
    }
}
