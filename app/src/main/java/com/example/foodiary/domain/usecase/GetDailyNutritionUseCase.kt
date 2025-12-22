package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.repository.MealRepository

class GetDailyNutritionUseCase(
    private val mealRepository: MealRepository,
    private val foodRepository: FoodRepository
) {

    suspend operator fun invoke(
        startOfDay: Long,
        endOfDay: Long
    ): DailyNutrition {

        val meals = mealRepository.getMealsForPeriod(startOfDay, endOfDay)

        var totalCalories = 0.0
        var totalProtein = 0.0
        var totalFat = 0.0
        var totalCarbs = 0.0

        val mealsByType = mutableMapOf<MealType, Int>()

        meals.forEach { meal ->
            val food = foodRepository.getFoodById(meal.foodId) ?: return@forEach
            val factor = meal.quantityInGrams / 100.0

            totalCalories += food.caloriesPer100g * factor
            totalProtein += food.proteinPer100g * factor
            totalFat += food.fatPer100g * factor
            totalCarbs += food.carbsPer100g * factor

            mealsByType[meal.mealType] =
                (mealsByType[meal.mealType] ?: 0) + 1
        }

        return DailyNutrition(
            totalCalories = totalCalories,
            totalProtein = totalProtein,
            totalFat = totalFat,
            totalCarbs = totalCarbs,
            mealsCount = meals.size,
            mealsByType = mealsByType
        )
    }
}
