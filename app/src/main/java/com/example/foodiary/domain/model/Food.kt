package com.example.foodiary.domain.model

data class Food(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double
)