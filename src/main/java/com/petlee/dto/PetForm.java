package com.petlee.dto;

/**
 * The body of {@code POST /api/pets} and {@code PUT /api/pets/{id}}.
 *
 * <p>{@code size} and {@code gender} arrive as strings and stay strings here. T-15 parses them,
 * so an unknown value becomes a 400 it can explain rather than a deserialisation failure the
 * caller cannot read. {@code categoryId} is resolved to a {@code Category} there too — a form
 * never becomes an entity in this package.
 */
public class PetForm {

    private String name;
    private String breed;
    private Integer age;
    private String size;
    private String gender;
    private String shortDesc;
    private String longDesc;
    private Integer categoryId;

    public PetForm() {
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

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }
}
