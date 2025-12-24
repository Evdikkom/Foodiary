package com.example.foodiary.domain.model

data class FoodSearchItem(
    val barcode: String,
    val name: String,
    val brand: String?,
    val caloriesPer100g: Double?,
    val proteinPer100g: Double?,
    val fatPer100g: Double?,
    val carbsPer100g: Double?
)
