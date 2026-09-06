package com.petlee.dto;

import jakarta.json.bind.annotation.JsonbNillable;

/**
 * A pet as the gallery lists it — the main image only, no owner contact details.
 *
 * <p>{@code size}, {@code gender} and {@code status} are the contract's uppercase enum strings,
 * held as {@code String} so an unrecognised value is a mapping decision rather than a
 * deserialisation failure.
 *
 * <h2>Why {@code @JsonbNillable}</h2>
 * JSON-B omits a null property by default, so a pet with no photograph would answer without a
 * {@code mainImageUrl} key at all, and a guest's pet detail would arrive with the three owner
 * fields simply missing. Every client reads an absent key as null, so nothing breaks — but the
 * frozen contract shows those keys, T-22 criterion 4 asks to see them as null rather than gone,
 * and a fixed key set is one less thing for a reviewer diffing a response against
 * {@code api-contract.md} to have to reason about.
 */
@JsonbNillable
public class PetDTO {

    private Long id;
    private String name;
    private String shortDesc;
    private Integer age;
    private String size;
    private String gender;
    private String status;
    private String categoryName;
    private String mainImageUrl;

    public PetDTO() {
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

    public String getShortDesc() {
        return shortDesc;
    }

    public void setShortDesc(String shortDesc) {
        this.shortDesc = shortDesc;
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

    public String getMainImageUrl() {
        return mainImageUrl;
    }

    public void setMainImageUrl(String mainImageUrl) {
        this.mainImageUrl = mainImageUrl;
    }
}
