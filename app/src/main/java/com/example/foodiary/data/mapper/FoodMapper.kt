package com.example.foodiary.data.mapper

import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.domain.model.Food

fun FoodEntity.toDomain(): Food {
    return Food(
        id = id,
        name = name,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g
    )
}
