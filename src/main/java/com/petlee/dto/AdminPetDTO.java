package com.petlee.dto;

/**
 * The gallery shape plus the two columns an administrator moderates by — {@code GET /api/admin/pets}
 * only.
 *
 * <p>It extends {@link PetDTO} rather than adding fields to it, because {@code PetDTO}'s key set is
 * frozen by {@code api-contract.md} and is what every public endpoint answers with. T-35's table
 * needs the owner ("who do I talk to about this listing") and the date ("is this one of this
 * morning's spam"), and neither belongs in a body a guest can fetch. Recorded as ADR-002 #12.
 */
public class AdminPetDTO extends PetDTO {

    private String ownerName;

    /** ISO-8601 local date-time, or {@code null} for a pet that has never been persisted. */
    private String createdAt;

    public AdminPetDTO() {
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
