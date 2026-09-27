package com.petlee.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One photograph of a pet. A pet has up to five; once it has any, exactly one is main, which the
 * partial unique index {@code ux_pet_image_main} guarantees at the database.
 */
@Entity
@Table(name = "pet_image")
public class PetImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Column(name = "image_url", nullable = false, length = 512)
    private String imageUrl;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /** For JPA. */
    public PetImage() {
    }

    public PetImage(Pet pet, String imageUrl, boolean main) {
        this.pet = pet;
        this.imageUrl = imageUrl;
        this.main = main;
    }

    @PrePersist
    protected void onUploaded() {
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getImageId() { return imageId; }

    public Pet getPet() { return pet; }

    public String getImageUrl() { return imageUrl; }

    public boolean isMain() { return main; }

    public void setMain(boolean main) { this.main = main; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
