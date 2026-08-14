package com.example.foodiary.presentation.model

import com.example.foodiary.domain.model.MealType

data class MealItemUi(
    val id: Long,
    val timeText: String,
    val mealType: MealType,
    val foodName: String,
    val gramsText: String
)
