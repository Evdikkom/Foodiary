package com.example.foodiary.domain.model

data class FoodPortionMemory(
    val preferredQuantityInGrams: Double?,
    val lastQuantityInGrams: Double?,
    val favoriteQuantityInGrams: Double?,
    val basedOnMealsCount: Int,
    val isMealTypeSpecific: Boolean,
)
