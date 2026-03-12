package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class FoodEntity(

    @PrimaryKey
    val id: String,

    val name: String,
    val imageUrl: String? = null,

    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double,

    val category: String = "other",
    val isCustom: Boolean = false
)