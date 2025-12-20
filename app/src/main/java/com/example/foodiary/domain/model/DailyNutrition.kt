package com.example.foodiary.domain.model

/**
 * DailyNutrition — агрегированная доменная модель суточного питания.
 * Формируется системой на основе списка приёмов пищи
 * и справочных данных о продуктах.
 */
data class DailyNutrition(
    val totalCalories: Double,
    val totalProtein: Double,
    val totalFat: Double,
    val totalCarbs: Double
)
