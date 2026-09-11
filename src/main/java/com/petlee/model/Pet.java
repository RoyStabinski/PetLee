package com.petlee.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pet")
public class Pet {

    public enum PetSize{
        SMALL, MEDIUM, LARGE;
    }

    public enum PetGender{
        MALE, FEMALE;
    }

    public enum PetStatus{
        AVAILABLE, ADOPTED, REMOVED;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pet_id")
    private Long petId;

    @Column(name = "pet_name", nullable = false, length = 100)
    private String petName;

    @Column(name = "breed", length = 100)
    private String breed;

    @Column(name = "age")
    private Integer age;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender",nullable = false)
    private PetGender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false)
    private PetSize size;

    @Column(name = "short_desc", length = 255)
    private String shortDesc;

    @Column(name = "long_desc", columnDefinition = "TEXT")
    private String longDesc;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PetStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    // updatable = false: specification §11 orders the gallery newest-first on created_at, so an
    // edit must never silently reorder listings. ADR-002 #4.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Explicit, so that adding a second constructor later cannot silently remove the
    // no-arg one JPA requires.
    public Pet(){
    }

    @PrePersist
        protected void onCreated(){
        this.createdAt = LocalDateTime.now();
        if(this.status == null){
            this.status = PetStatus.AVAILABLE;
        }
    }

    public Long getPetId(){
        return petId;
    }
    
    public void setPetId(Long petId){
        this.petId = petId;
    }
    
    public String getPetName(){
        return petName;
    }
    
    public void setPetName(String petName){
        this.petName = petName;
    }
    
    public String getBreed(){
        return breed;
    }
    
    public void setBreed(String breed){
        this.breed = breed;
    }
    
    public Integer getAge(){
        return age;
    }
    
    public void setAge(Integer age){
        this.age = age;
    }
    
    // Named getSize/setSize rather than with the old pet- prefix: JSON-B and JSF EL both
    // derive the property name from the accessor, so the previous names serialised the
    // contract's "size" key as "petSize". ADR-002 #2.
    public PetSize getSize(){
        return size;
    }

    public void setSize(PetSize size){
        this.size = size;
    }

    public String getShortDesc(){
        return shortDesc;
    }

    public void setShortDesc(String shortDesc){
        this.shortDesc = shortDesc;
    }

    public PetGender getGender(){
        return gender;
    }

    public void setGender(PetGender gender){
        this.gender = gender;
    }

    public String getLongDesc(){
        return longDesc;
    }

    public void setLongDesc(String longDesc){
        this.longDesc = longDesc;
    }

    public PetStatus getStatus(){
        return status;
    }

    public void setStatus(PetStatus status){
        this.status = status;
    }

    public Category getCategory(){
        return category;
    }

    public void setCategory(Category category){
        this.category = category;
    }

    public User getOwner(){
        return owner;
    }

    public void setOwner(User owner){
        this.owner = owner;
    }

    public String getImageUrl(){
        return imageUrl;
    }

    public void setImageUrl(String imageUrl){
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getCreatedAt(){
        return createdAt;
    }

    // Long, not long: version is null until the row is first written, and unboxing that null
    // threw NullPointerException on every unpersisted Pet. ADR-002 #3.
    // No setter — the persistence provider owns this field.
    public Long getVersion(){
        return version;
    }

    // Identity comparison on the id alone. Two unpersisted pets are never equal, even when
    // every other field matches — they are two distinct rows waiting to be written.
    @Override
    public boolean equals(Object o){
        if(this == o){
            return true;
        }
        if(!(o instanceof Pet other)){
            return false;
        }
        return petId != null && petId.equals(other.petId);
    }

    // Constant, deliberately. A hash derived from the id would change when the provider
    // assigns one on persist, and an entity already inside a HashSet would become unfindable.
    @Override
    public int hashCode(){
        return Pet.class.hashCode();
    }

    @Override
    public String toString(){
        return "Pet{petId=" + petId + ", petName='" + petName + "'}";
    }
}
