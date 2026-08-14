package com.example.foodiary.domain.model

data class HistoryMealTemplate(
    val id: String,
    val mealType: MealType,
    val title: String,
    val items: List<HistoryMealTemplateItem>,
    val occurrencesCount: Int,
    val totalWeightInGrams: Double,
    val totalCalories: Double,
    val lastUsedAt: Long,
) : java.io.Serializable

data class HistoryMealTemplateItem(
    val foodId: String,
    val foodName: String,
    val imageUrl: String?,
    val quantityInGrams: Double,
) : java.io.Serializable
