package com.petlee.model;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    // unique = true is documentation only; ux_category_name_lower on LOWER(category_name) is
    // the constraint.
    @Column(name = "category_name", nullable = false, unique = true, length = 50)
    private String categoryName;

    public Category(){}

    public Category(String categoryName){
        this.categoryName = categoryName;
    }

    public Integer getCategoryId(){ return categoryId; }

    public void setCategoryId(Integer categoryId){ this.categoryId = categoryId; }

    public String getCategoryName(){ return categoryName; }

    public void setCategoryName(String categoryName){ this.categoryName = categoryName; }
}
