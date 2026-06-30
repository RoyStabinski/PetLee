package com.petlee.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "pet_name", nullable = false)
    private String petName;

    @Column(name = "breed")
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

    @OneToMany(mappedBy = "pet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PetImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = true)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version")
    private Long version;

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
    
    public PetSize getPetSize(){
        return size;
    }

    public void setPetSize(PetSize size){
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

    public List<PetImage> getImages(){
        return images;
    }

    public void setImages(List<PetImage> images){
        this.images = images;
    }

    public LocalDateTime getCreatedAt(){
        return createdAt;
    }

    public long getVersion(){
        return version;
    }
}