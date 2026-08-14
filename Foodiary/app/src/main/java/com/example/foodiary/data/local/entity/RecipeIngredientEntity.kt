package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_ingredients")
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipeId: String,
    val foodId: String,
    val grams: Double,
    val position: Int
)
