package com.petlee.model;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    // unique = true is documentation only (schema generation is off). The real constraint is
    // ux_category_name_lower, a unique index on LOWER(category_name): one label, one category,
    // whatever case it is typed in.
    @Column(name = "category_name", nullable = false, unique = true, length = 50)
    private String categoryName;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Pet> pets = new ArrayList<>();

    public Category(){}

    public Category(String categoryName){
        this.categoryName = categoryName;
    }

    public Integer getCategoryId(){
        return categoryId;
    }

    public void setCategoryId(Integer categoryId){
        this.categoryId = categoryId;
    }

    public String getCategoryName(){
        return categoryName;
    }

    public void setCategoryName(String categoryName){
        this.categoryName = categoryName;
    }

    public List<Pet> getPets(){
        return pets;
    }

    public void setPets(List<Pet>pets){
        this.pets = pets;
    }
}
