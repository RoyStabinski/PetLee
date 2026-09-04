package com.petlee.dto;

/** One image: {@code id}, {@code imageUrl}, {@code isMain}. */
public class PetImageDTO {

    private Integer id;
    private String imageUrl;
    private boolean isMain;

    public PetImageDTO() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    // getIsMain, not isMain: JSON-B derives the key from the accessor, and a getter named
    // isMain() would emit "main" instead of the contract's "isMain".
    public boolean getIsMain() {
        return isMain;
    }

    public void setIsMain(boolean isMain) {
        this.isMain = isMain;
    }
}
