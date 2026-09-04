package com.petlee.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * A pet as the details page shows it: every field, every image, and the owner's contact details.
 *
 * <p>The three owner fields are {@code null} for a caller who is not logged in — see
 * {@code PetMapper.toDetailDto}, which is the only place that decision is made.
 */
public class PetDetailDTO {

    private Long id;
    private String name;
    private String breed;
    private Integer age;
    private String size;
    private String gender;
    private String shortDesc;
    private String longDesc;
    private String status;
    private String categoryName;
    private List<PetImageDTO> images = new ArrayList<>();
    private String ownerFullName;
    private String ownerEmail;
    private String ownerPhone;

    public PetDetailDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getShortDesc() {
        return shortDesc;
    }

    public void setShortDesc(String shortDesc) {
        this.shortDesc = shortDesc;
    }

    public String getLongDesc() {
        return longDesc;
    }

    public void setLongDesc(String longDesc) {
        this.longDesc = longDesc;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public List<PetImageDTO> getImages() {
        return images;
    }

    public void setImages(List<PetImageDTO> images) {
        this.images = images;
    }

    public String getOwnerFullName() {
        return ownerFullName;
    }

    public void setOwnerFullName(String ownerFullName) {
        this.ownerFullName = ownerFullName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getOwnerPhone() {
        return ownerPhone;
    }

    public void setOwnerPhone(String ownerPhone) {
        this.ownerPhone = ownerPhone;
    }
}
