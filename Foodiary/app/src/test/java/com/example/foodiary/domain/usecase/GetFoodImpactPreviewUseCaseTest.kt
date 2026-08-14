package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.FoodImpactTone
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.ProteinGoalBasis
import com.example.foodiary.testing.food
import com.example.foodiary.testing.manualConflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetFoodImpactPreviewUseCaseTest {

    private val useCase = GetFoodImpactPreviewUseCase()

    @Test
    fun `preview shows calorie overload before product is saved`() {
        val dessert = food(
            id = "dessert",
            name = "Cheesecake",
            caloriesPer100g = 420.0,
            proteinPer100g = 6.0,
            fatPer100g = 24.0,
            carbsPer100g = 48.0
        )

        val preview = useCase(
            food = dessert,
            quantityInGrams = 180.0,
            currentNutrition = nutrition(totalCalories = 1750.0, totalProtein = 80.0),
            targets = targets()
        )

        assertTrue(preview.projectedCalories > 2_000.0)
        assertTrue(preview.score < 70)
        assertNotNull(preview.recommendedQuantityInGrams)
        assertTrue(preview.macroStatuses.any { it.label == "Калории" && it.tone == FoodImpactTone.DANGER })
    }

    @Test
    fun `preview rewards product that closes protein without calorie overload`() {
        val chicken = food(
            id = "chicken",
            name = "Chicken breast",
            caloriesPer100g = 165.0,
            proteinPer100g = 31.0,
            fatPer100g = 3.0,
            carbsPer100g = 0.0
        )

        val preview = useCase(
            food = chicken,
            quantityInGrams = 160.0,
            currentNutrition = nutrition(
                totalCalories = 1400.0,
                totalProtein = 55.0,
                totalFat = 54.0,
                totalCarbs = 180.0
            ),
            targets = targets()
        )

        assertTrue(preview.score >= 75)
        assertTrue(preview.title.contains("белков"))
        assertTrue(preview.projectedProtein >= 100.0)
    }

    @Test
    fun `recommended portion stays stable when selected quantity changes`() {
        val cottageCheese = food(
            id = "cottage-cheese",
            name = "Cottage cheese 5%",
            caloriesPer100g = 83.0,
            proteinPer100g = 11.7,
            fatPer100g = 5.0,
            carbsPer100g = 3.0
        )
        val currentNutrition = nutrition(
            totalCalories = 500.0,
            totalProtein = 30.0,
            totalFat = 20.0,
            totalCarbs = 60.0
        )

        val initialPreview = useCase(
            food = cottageCheese,
            quantityInGrams = 100.0,
            currentNutrition = currentNutrition,
            targets = targets()
        )
        val adjustedPreview = useCase(
            food = cottageCheese,
            quantityInGrams = initialPreview.recommendedQuantityInGrams ?: 300.0,
            currentNutrition = currentNutrition,
            targets = targets()
        )

        assertNotNull(initialPreview.recommendedQuantityInGrams)
        assertEquals(initialPreview.recommendedQuantityInGrams, adjustedPreview.recommendedQuantityInGrams)
    }

    @Test
    fun `preview lowers score when allergen conflict is present`() {
        val milk = food(
            id = "milk",
            name = "Milk",
            caloriesPer100g = 64.0,
            proteinPer100g = 3.2,
            fatPer100g = 3.6,
            carbsPer100g = 4.8
        )

        val preview = useCase(
            food = milk,
            quantityInGrams = 200.0,
            currentNutrition = nutrition(totalCalories = 900.0, totalProtein = 55.0),
            targets = targets(),
            safetyProfile = com.example.foodiary.domain.model.FoodSafetyProfile(
                highRiskConflicts = listOf(manualConflict("milk"))
            )
        )

        assertEquals("Есть риск по ограничениям", preview.title)
        assertTrue(preview.score < 60)
        assertTrue(preview.details.any { it.label == "Ограничения" && it.tone == FoodImpactTone.DANGER })
    }

    private fun nutrition(
        totalCalories: Double = 0.0,
        totalProtein: Double = 0.0,
        totalFat: Double = 0.0,
        totalCarbs: Double = 0.0
    ): DailyNutrition {
        return DailyNutrition(
            totalCalories = totalCalories,
            totalProtein = totalProtein,
            totalFat = totalFat,
            totalCarbs = totalCarbs,
            mealsCount = 0,
            mealsByType = emptyMap(),
            caloriesByMealType = emptyMap()
        )
    }

    private fun targets(): NutritionTargets {
        return NutritionTargets(
            maintenanceCalories = 2_200,
            targetCalories = 2_000,
            proteinGrams = 100,
            fatGrams = 67,
            carbsGrams = 250,
            proteinGoalBasis = ProteinGoalBasis.TOTAL_BODY_WEIGHT,
            proteinReferenceWeightKg = 80.0
        )
    }
}
