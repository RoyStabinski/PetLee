package com.petlee.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A pet a member has saved. The key is the pair itself, so saving the same pet twice is refused
 * by the database; {@link #getCreatedAt()} orders the member's list, newest first. Only ever
 * inserted or deleted, never updated.
 */
@Entity
@Table(name = "favorite")
public class Favorite {

    @EmbeddedId
    private FavoriteId id;

    /** Mapped onto the key: user_id is both this reference and the key's userId. */
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("petId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** For JPA. */
    public Favorite() {
    }

    public Favorite(User user, Pet pet) {
        this.user = user;
        this.pet = pet;
        this.id = new FavoriteId(user.getUserId(), pet.getPetId());
    }

    @PrePersist
    protected void onSaved() {
        this.createdAt = LocalDateTime.now();
    }

    public FavoriteId getId() { return id; }

    public User getUser() { return user; }

    public Pet getPet() { return pet; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
