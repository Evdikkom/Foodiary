package com.example.foodiary.data.mapper

import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.domain.model.Meal

/**
 * MealMapper — маппинг (маппинг — преобразование моделей) между:
 * - MealEntity (entity — модель таблицы Room)
 * - Meal (domain — доменная модель бизнес-логики)
 */

fun MealEntity.toDomain(): Meal {
    return Meal(
        foodId = foodId,
        quantityInGrams = quantityInGrams,
        mealType = mealType,
        timestamp = timestamp,
        note = note
    )
}

fun Meal.toEntity(): MealEntity {
    return MealEntity(
        foodId = foodId,
        quantityInGrams = quantityInGrams,
        mealType = mealType,
        timestamp = timestamp,
        note = note
    )
}
