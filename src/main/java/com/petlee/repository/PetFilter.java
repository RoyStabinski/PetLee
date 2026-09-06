package com.petlee.repository;

import com.petlee.model.Pet;

/**
 * Immutable gallery filter criteria: the contract's {@code ?categoryId=1&size=SMALL&gender=MALE}.
 *
 * <p>Every field is optional. A {@code null} field means "do not filter on this", so
 * {@link #none()} matches the whole catalogue. There are no setters — an instance can be passed
 * across layers and cached without anyone changing it underneath.
 *
 * <p>The criteria are <strong>typed</strong>, so an unknown query value can never reach SQL: it
 * fails when the REST tier (T-22) parses the string into an enum, not in the database.
 *
 * <pre>{@code
 * PetFilter filter = PetFilter.builder()
 *         .categoryId(1)
 *         .size(Pet.PetSize.SMALL)
 *         .build();
 * }</pre>
 */
public final class PetFilter {

    private static final PetFilter NONE = builder().build();

    private final Integer categoryId;
    private final Pet.PetSize size;
    private final Pet.PetGender gender;

    private PetFilter(Builder builder) {
        this.categoryId = builder.categoryId;
        this.size = builder.size;
        this.gender = builder.gender;
    }

    /**
     * @return a filter with no criteria set, matching every pet the calling query allows
     */
    public static PetFilter none() {
        return NONE;
    }

    /**
     * @return a new builder; unset criteria stay {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the category to restrict to, or {@code null} for any category
     */
    public Integer getCategoryId() {
        return categoryId;
    }

    /**
     * @return the size to restrict to, or {@code null} for any size
     */
    public Pet.PetSize getSize() {
        return size;
    }

    /**
     * @return the gender to restrict to, or {@code null} for any gender
     */
    public Pet.PetGender getGender() {
        return gender;
    }

    /**
     * @return {@code true} when no criterion is set
     */
    public boolean isEmpty() {
        return categoryId == null && size == null && gender == null;
    }

    @Override
    public String toString() {
        return "PetFilter{categoryId=" + categoryId + ", size=" + size + ", gender=" + gender + "}";
    }

    /**
     * Collects criteria, then builds one immutable {@link PetFilter}. Not thread-safe; the
     * filter it produces is.
     */
    public static final class Builder {

        private Integer categoryId;
        private Pet.PetSize size;
        private Pet.PetGender gender;

        private Builder() {
        }

        /**
         * @param categoryId the category id, or {@code null} to not filter on category
         */
        public Builder categoryId(Integer categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        /**
         * @param size the size, or {@code null} to not filter on size
         */
        public Builder size(Pet.PetSize size) {
            this.size = size;
            return this;
        }

        /**
         * @param gender the gender, or {@code null} to not filter on gender
         */
        public Builder gender(Pet.PetGender gender) {
            this.gender = gender;
            return this;
        }

        public PetFilter build() {
            return new PetFilter(this);
        }
    }
}
