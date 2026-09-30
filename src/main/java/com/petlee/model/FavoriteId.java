package com.petlee.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** The key of a {@link Favorite}: which member saved which pet. */
@Embeddable
public class FavoriteId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    /** For JPA. */
    public FavoriteId() {
    }

    public FavoriteId(Long userId, Long petId) {
        this.userId = userId;
        this.petId = petId;
    }

    public Long getUserId() { return userId; }

    public Long getPetId() { return petId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FavoriteId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(petId, that.petId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, petId);
    }
}
