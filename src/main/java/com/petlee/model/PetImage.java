
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

    // Defaulted here so a caller that never touches the flag cannot violate the NOT NULL
    // column. The database also defaults it, but an INSERT sent by JPA always names the
    // column, so the default would never apply.
    @Column(name = "is_main", nullable = false)
    private Boolean isMain = Boolean.FALSE;

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

    // Identity comparison on the id alone. Two unpersisted images are never equal, even when
    // every other field matches — they are two distinct rows waiting to be written.
    @Override
    public boolean equals(Object o){
        if(this == o){
            return true;
        }
        if(!(o instanceof PetImage other)){
            return false;
        }
        return imageId != null && imageId.equals(other.imageId);
    }

    // Constant, deliberately. A hash derived from the id would change when the provider
    // assigns one on persist, and an entity already inside a HashSet would become unfindable.
    @Override
    public int hashCode(){
        return PetImage.class.hashCode();
    }

    @Override
    public String toString(){
        return "PetImage{imageId=" + imageId + ", imageUrl='" + imageUrl + "'}";
    }
}
