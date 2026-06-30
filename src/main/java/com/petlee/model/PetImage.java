
package com.petlee.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;


@Entity
@Table(name = "pet_image")
public class PetImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Integer imageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Column(name = "image_url", nullable = false, length = 512)
    private String imageUrl;

    @Column(name = "is_main", nullable = false)
    private Boolean isMain;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpsert(){
        this.updatedAt = LocalDateTime.now();
    }

    public PetImage(){}

    public Integer getImageId(){
        return imageId;
    }

    public void setImageId(Integer imageId){
        this.imageId = imageId;
    }

    public Pet getPet(){
        return pet;
    }

    public void setPet(Pet pet){
        this.pet = pet;
    }

    public String getImageUrl(){
        return imageUrl;
    }

    public void setImageUrl(String imageUrl){
        this.imageUrl = imageUrl;
    }

    public Boolean getIsMain(){
        return isMain;
    }

    public void setIsMain(Boolean isMain){
        this.isMain = isMain;
    }

    public LocalDateTime getUpdatedAt(){
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt){
        this.updatedAt = updatedAt;
    }
}
