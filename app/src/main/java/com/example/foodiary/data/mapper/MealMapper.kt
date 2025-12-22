package com.example.foodiary.data.mapper

import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.domain.model.Meal

fun MealEntity.toDomain(): Meal {
    return Meal(
        foodId = foodId,
        quantityInGrams = quantityInGrams,
        mealType = mealType,
        timestamp = timestamp,
        note = note
    )
}
