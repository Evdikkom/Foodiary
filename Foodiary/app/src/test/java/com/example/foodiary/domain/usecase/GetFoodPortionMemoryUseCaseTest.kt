package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.MealType
import com.example.foodiary.testing.FakeMealRepository
import com.example.foodiary.testing.meal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetFoodPortionMemoryUseCaseTest {

    @Test
    fun `returns empty memory when food has no history`() = runBlocking {
        val useCase = GetFoodPortionMemoryUseCase(FakeMealRepository())

        val result = useCase(foodId = "oatmeal", mealType = MealType.BREAKFAST)

        assertNull(result.preferredQuantityInGrams)
        assertNull(result.lastQuantityInGrams)
        assertNull(result.favoriteQuantityInGrams)
        assertEquals(0, result.basedOnMealsCount)
        assertFalse(result.isMealTypeSpecific)
    }

    @Test
    fun `prefers meal type specific history when it exists`() = runBlocking {
        val now = System.currentTimeMillis()
        val meals = listOf(
            meal(foodId = "oatmeal", quantityInGrams = 103.0, mealType = MealType.BREAKFAST, timestamp = now),
            meal(foodId = "oatmeal", quantityInGrams = 101.0, mealType = MealType.BREAKFAST, timestamp = now - day(1)),
            meal(foodId = "oatmeal", quantityInGrams = 150.0, mealType = MealType.LUNCH, timestamp = now - day(2))
        )
        val useCase = GetFoodPortionMemoryUseCase(FakeMealRepository(meals))

        val result = useCase(foodId = "oatmeal", mealType = MealType.BREAKFAST)

        assertNotNull(result.lastQuantityInGrams)
        assertNotNull(result.favoriteQuantityInGrams)
        assertNotNull(result.preferredQuantityInGrams)
        assertEquals(103.0, result.lastQuantityInGrams!!, 0.001)
        assertEquals(105.0, result.favoriteQuantityInGrams!!, 0.001)
        assertEquals(105.0, result.preferredQuantityInGrams!!, 0.001)
        assertEquals(2, result.basedOnMealsCount)
        assertTrue(result.isMealTypeSpecific)
    }

    @Test
    fun `falls back to all meal history when specific scope is empty`() = runBlocking {
        val now = System.currentTimeMillis()
        val meals = listOf(
            meal(foodId = "banana", quantityInGrams = 90.0, mealType = MealType.SNACK, timestamp = now),
            meal(foodId = "banana", quantityInGrams = 92.0, mealType = MealType.SNACK, timestamp = now - day(1)),
            meal(foodId = "banana", quantityInGrams = 150.0, mealType = MealType.LUNCH, timestamp = now - day(2))
        )
        val useCase = GetFoodPortionMemoryUseCase(FakeMealRepository(meals))

        val result = useCase(foodId = "banana", mealType = MealType.DINNER)

        assertNotNull(result.lastQuantityInGrams)
        assertNotNull(result.favoriteQuantityInGrams)
        assertNotNull(result.preferredQuantityInGrams)
        assertEquals(90.0, result.lastQuantityInGrams!!, 0.001)
        assertEquals(90.0, result.favoriteQuantityInGrams!!, 0.001)
        assertEquals(90.0, result.preferredQuantityInGrams!!, 0.001)
        assertEquals(3, result.basedOnMealsCount)
        assertFalse(result.isMealTypeSpecific)
    }

    private fun day(days: Long): Long = days * 24L * 60L * 60L * 1000L
}
