package com.example.foodiary.domain.model

/**
 * DailyNutrition — агрегированная доменная модель,
 * описывающая суточное питание пользователя.
 */
data class DailyNutrition(

    val totalCalories: Double,
    val totalProtein: Double,
    val totalFat: Double,
    val totalCarbs: Double,

    val mealsCount: Int,
    val mealsByType: Map<MealType, Int>
) {

    companion object {

        /**
         * Формирует агрегированные показатели питания
         * на основе списка приёмов пищи.
         */
        fun fromMeals(meals: List<Meal>): DailyNutrition {

            var calories = 0.0
            var protein = 0.0
            var fat = 0.0
            var carbs = 0.0

            val mealsByType = mutableMapOf<MealType, Int>()

            for (meal in meals) {
                // ⚠️ Пока считаем условно.
                // Реальные значения будут подтягиваться из FoodEntity позже.
                calories += meal.quantityInGrams * 1.0
                protein += meal.quantityInGrams * 0.1
                fat += meal.quantityInGrams * 0.05
                carbs += meal.quantityInGrams * 0.2

                mealsByType[meal.mealType] =
                    (mealsByType[meal.mealType] ?: 0) + 1
            }

            return DailyNutrition(
                totalCalories = calories,
                totalProtein = protein,
                totalFat = fat,
                totalCarbs = carbs,
                mealsCount = meals.size,
                mealsByType = mealsByType
            )
        }
    }
}
