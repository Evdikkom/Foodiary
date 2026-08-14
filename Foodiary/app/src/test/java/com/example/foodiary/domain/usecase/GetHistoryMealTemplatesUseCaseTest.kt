package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.MealType
import com.example.foodiary.testing.FakeFoodRepository
import com.example.foodiary.testing.FakeMealRepository
import com.example.foodiary.testing.food
import com.example.foodiary.testing.meal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetHistoryMealTemplatesUseCaseTest {

    @Test
    fun `builds exact template from repeated identical days`() = runBlocking {
        val now = System.currentTimeMillis()
        val foods = listOf(
            food("oatmeal", name = "Овсянка", category = "grain"),
            food("banana", name = "Банан", category = "fruit")
        )
        val meals = listOf(
            meal(foodId = "oatmeal", quantityInGrams = 80.0, mealType = MealType.BREAKFAST, timestamp = now - day(1)),
            meal(foodId = "banana", quantityInGrams = 120.0, mealType = MealType.BREAKFAST, timestamp = now - day(1)),
            meal(foodId = "oatmeal", quantityInGrams = 80.0, mealType = MealType.BREAKFAST, timestamp = now - day(2)),
            meal(foodId = "banana", quantityInGrams = 120.0, mealType = MealType.BREAKFAST, timestamp = now - day(2))
        )

        val useCase = GetHistoryMealTemplatesUseCase(
            mealRepository = FakeMealRepository(meals),
            foodRepository = FakeFoodRepository(foods)
        )

        val result = useCase(MealType.BREAKFAST, limit = 5)

        assertEquals(1, result.size)
        assertEquals(2, result.first().occurrencesCount)
        assertEquals(setOf("oatmeal", "banana"), result.first().items.map { it.foodId }.toSet())
        assertEquals(200.0, result.first().totalWeightInGrams, 0.001)
    }

    @Test
    fun `builds soft template from overlapping meal sets`() = runBlocking {
        val now = System.currentTimeMillis()
        val foods = listOf(
            food("chicken", name = "Курица", proteinPer100g = 28.0, category = "protein"),
            food("rice", name = "Рис", carbsPer100g = 28.0, category = "grain"),
            food("tomato", name = "Помидор", category = "vegetable"),
            food("cucumber", name = "Огурец", category = "vegetable")
        )
        val meals = listOf(
            meal(foodId = "chicken", quantityInGrams = 150.0, mealType = MealType.LUNCH, timestamp = now - day(1)),
            meal(foodId = "rice", quantityInGrams = 120.0, mealType = MealType.LUNCH, timestamp = now - day(1)),
            meal(foodId = "tomato", quantityInGrams = 80.0, mealType = MealType.LUNCH, timestamp = now - day(1)),
            meal(foodId = "chicken", quantityInGrams = 170.0, mealType = MealType.LUNCH, timestamp = now - day(2)),
            meal(foodId = "rice", quantityInGrams = 130.0, mealType = MealType.LUNCH, timestamp = now - day(2)),
            meal(foodId = "cucumber", quantityInGrams = 60.0, mealType = MealType.LUNCH, timestamp = now - day(2))
        )

        val useCase = GetHistoryMealTemplatesUseCase(
            mealRepository = FakeMealRepository(meals),
            foodRepository = FakeFoodRepository(foods)
        )

        val result = useCase(MealType.LUNCH, limit = 5)

        assertTrue(result.isNotEmpty())
        val template = result.first { candidate ->
            candidate.items.map { it.foodId }.toSet() == setOf("chicken", "rice")
        }
        assertEquals(2, template.occurrencesCount)
        assertEquals(285.0, template.totalWeightInGrams, 0.001)
    }

    @Test
    fun `ignores single occurrence history`() = runBlocking {
        val now = System.currentTimeMillis()
        val useCase = GetHistoryMealTemplatesUseCase(
            mealRepository = FakeMealRepository(
                listOf(
                    meal(
                        foodId = "salmon",
                        quantityInGrams = 160.0,
                        mealType = MealType.DINNER,
                        timestamp = now - day(1)
                    )
                )
            ),
            foodRepository = FakeFoodRepository(
                listOf(food("salmon", name = "Лосось", category = "protein"))
            )
        )

        val result = useCase(MealType.DINNER, limit = 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `ignores matching meals outside history window`() = runBlocking {
        val now = System.currentTimeMillis()
        val useCase = GetHistoryMealTemplatesUseCase(
            mealRepository = FakeMealRepository(
                listOf(
                    meal(
                        foodId = "oatmeal",
                        quantityInGrams = 80.0,
                        mealType = MealType.BREAKFAST,
                        timestamp = now - day(1)
                    ),
                    meal(
                        foodId = "oatmeal",
                        quantityInGrams = 80.0,
                        mealType = MealType.BREAKFAST,
                        timestamp = now - day(120)
                    )
                )
            ),
            foodRepository = FakeFoodRepository(
                listOf(food("oatmeal", name = "РћРІСЃСЏРЅРєР°", category = "grain"))
            )
        )

        val result = useCase(MealType.BREAKFAST, limit = 5)

        assertTrue(result.isEmpty())
    }

    private fun day(days: Long): Long = days * 24L * 60L * 60L * 1000L
}
