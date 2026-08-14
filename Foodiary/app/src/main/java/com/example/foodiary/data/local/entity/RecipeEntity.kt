package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: String,
    val foodId: String,
    val name: String,
    val imageUrl: String? = null,
    val description: String = "",
    val totalWeightInGrams: Double,
    val servingWeightInGrams: Double,
    val createdAt: Long = System.currentTimeMillis()
)
