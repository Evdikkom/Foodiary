package com.example.foodiary.data.mapper

import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.domain.model.Food

fun FoodEntity.toDomain(): Food {
    return Food(
        id = id,
        name = name,
        imageUrl = imageUrl,
        caloriesPer100g = normalizeCaloriesPer100g(caloriesPer100g),
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g,
        isCustom = isCustom,
        category = category
    )
}

private fun normalizeCaloriesPer100g(value: Double): Double {
    return if (value > 950.0) {
        value / 4.184
    } else {
        value
    }
}
