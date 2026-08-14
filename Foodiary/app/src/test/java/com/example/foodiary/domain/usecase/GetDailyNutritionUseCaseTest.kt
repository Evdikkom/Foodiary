package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.MealType
import com.example.foodiary.testing.FakeFoodRepository
import com.example.foodiary.testing.FakeMealRepository
import com.example.foodiary.testing.food
import com.example.foodiary.testing.meal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GetDailyNutritionUseCaseTest {

    @Test
    fun `calculates totals and breakdown per meal type`() = runBlocking {
        val foods = listOf(
            food(
                id = "rice",
                name = "Рис",
                caloriesPer100g = 130.0,
                proteinPer100g = 2.7,
                fatPer100g = 0.3,
                carbsPer100g = 28.0,
                category = "grain"
            ),
            food(
                id = "chicken",
                name = "Курица",
                caloriesPer100g = 165.0,
                proteinPer100g = 31.0,
                fatPer100g = 3.6,
                carbsPer100g = 0.0,
                category = "protein"
            )
        )
        val now = System.currentTimeMillis()
        val meals = listOf(
            meal(foodId = "rice", quantityInGrams = 200.0, mealType = MealType.LUNCH, timestamp = now),
            meal(foodId = "chicken", quantityInGrams = 150.0, mealType = MealType.DINNER, timestamp = now)
        )

        val useCase = GetDailyNutritionUseCase(
            mealRepository = FakeMealRepository(meals),
            foodRepository = FakeFoodRepository(foods)
        )

        val result = useCase(now - 1_000L, now + 1_000L)

        assertEquals(507.5, result.totalCalories, 0.001)
        assertEquals(51.9, result.totalProtein, 0.001)
        assertEquals(6.0, result.totalFat, 0.001)
        assertEquals(56.0, result.totalCarbs, 0.001)
        assertEquals(2, result.mealsCount)
        assertEquals(1, result.mealsByType[MealType.LUNCH])
        assertEquals(1, result.mealsByType[MealType.DINNER])
        assertEquals(260.0, result.caloriesByMealType[MealType.LUNCH]!!, 0.001)
        assertEquals(247.5, result.caloriesByMealType[MealType.DINNER]!!, 0.001)
    }
}
