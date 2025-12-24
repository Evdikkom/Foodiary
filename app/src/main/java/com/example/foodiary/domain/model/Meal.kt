package com.example.foodiary.domain.model

/**
 * Meal — доменная модель приёма пищи.
 * Описывает факт потребления конкретного продукта
 * в определённом количестве и времени.
 */
data class Meal(
    val id: Long = 0,
    val foodId: String,
    val quantityInGrams: Double,
    val mealType: MealType,
    val timestamp: Long,
    val note: String = ""
)
