package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.FoodPortionMemory
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.MealRepository

class GetFoodPortionMemoryUseCase(
    private val mealRepository: MealRepository
) {

    suspend operator fun invoke(
        foodId: String,
        mealType: MealType? = null
    ): FoodPortionMemory {
        val now = System.currentTimeMillis()
        val historyStart = now - HISTORY_WINDOW_MS
        val allMeals = mealRepository.getMealsForPeriod(historyStart, now + 1)
            .filter { it.foodId == foodId }
            .sortedByDescending { it.timestamp }

        if (allMeals.isEmpty()) {
            return FoodPortionMemory(
                preferredQuantityInGrams = null,
                lastQuantityInGrams = null,
                favoriteQuantityInGrams = null,
                basedOnMealsCount = 0,
                isMealTypeSpecific = false,
            )
        }

        val scopedMeals = mealType?.let { target ->
            allMeals.filter { it.mealType == target }
        }.orEmpty()
        val activeMeals = if (scopedMeals.isNotEmpty()) scopedMeals else allMeals

        val lastQuantity = activeMeals.firstOrNull()?.quantityInGrams
        val favoriteQuantity = activeMeals
            .groupBy { normalizePortion(it.quantityInGrams) }
            .entries
            .maxWithOrNull(
                compareBy<Map.Entry<Double, List<com.example.foodiary.domain.model.Meal>>> { it.value.size }
                    .thenBy { entry -> entry.value.maxOfOrNull { it.timestamp } ?: 0L }
            )
            ?.key
            ?.takeIf { activeMeals.size >= 2 }

        return FoodPortionMemory(
            preferredQuantityInGrams = favoriteQuantity ?: lastQuantity,
            lastQuantityInGrams = lastQuantity,
            favoriteQuantityInGrams = favoriteQuantity,
            basedOnMealsCount = activeMeals.size,
            isMealTypeSpecific = scopedMeals.isNotEmpty(),
        )
    }

    private fun normalizePortion(value: Double): Double {
        if (value <= 0.0) return 0.0
        return (kotlin.math.round(value / PORTION_STEP_GRAMS) * PORTION_STEP_GRAMS)
            .coerceAtLeast(PORTION_STEP_GRAMS)
    }

    private companion object {
        const val PORTION_STEP_GRAMS = 5.0
        const val HISTORY_WINDOW_MS = 120L * 24L * 60L * 60L * 1000L
    }
}
