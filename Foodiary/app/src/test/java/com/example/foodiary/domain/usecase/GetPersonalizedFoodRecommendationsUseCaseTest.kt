package com.example.foodiary.domain.usecase

import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.ProteinGoalBasis
import com.example.foodiary.testing.FakeAllergenRepository
import com.example.foodiary.testing.FakeFavoriteFoodsRepository
import com.example.foodiary.testing.FakeFoodRepository
import com.example.foodiary.testing.FakeMealRepository
import com.example.foodiary.testing.FakeUserRepository
import com.example.foodiary.testing.food
import com.example.foodiary.testing.inferredConflict
import com.example.foodiary.testing.manualConflict
import com.example.foodiary.testing.safetyProfileWithWarning
import com.example.foodiary.testing.user
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetPersonalizedFoodRecommendationsUseCaseTest {

    @Test
    fun `returns empty list when there is no user and no behavior signals`() = runBlocking {
        val foodRepository = FakeFoodRepository()
        val mealRepository = FakeMealRepository()
        val useCase = buildUseCase(
            foodRepository = foodRepository,
            mealRepository = mealRepository,
            userRepository = FakeUserRepository(null)
        )

        val result = useCase(limit = 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `macro aligned protein food outranks carb focused alternative`() = runBlocking {
        val foods = listOf(
            food(
                id = "chicken_breast",
                name = "Chicken breast",
                caloriesPer100g = 165.0,
                proteinPer100g = 31.0,
                fatPer100g = 3.6,
                carbsPer100g = 0.0,
                category = "protein"
            ),
            food(
                id = "rice",
                name = "Rice",
                caloriesPer100g = 130.0,
                proteinPer100g = 2.7,
                fatPer100g = 0.3,
                carbsPer100g = 28.0,
                category = "grain"
            )
        )
        val user = user(
            goal = UserGoal.MUSCLE_GAIN_TRAINING,
            activityLevel = ActivityLevel.VERY_ACTIVE
        )
        val useCase = buildUseCase(
            foodRepository = FakeFoodRepository(foods),
            mealRepository = FakeMealRepository(),
            userRepository = FakeUserRepository(user),
            favoriteFoodsRepository = FakeFavoriteFoodsRepository(),
            allergenRepository = FakeAllergenRepository()
        )

        val result = useCase(mealType = MealType.LUNCH, limit = 2)

        assertEquals(2, result.size)
        assertEquals("chicken_breast", result.first().food.id)
        assertTrue(result.first().breakdown.macroGapScore >= result.last().breakdown.macroGapScore)
    }

    @Test
    fun `high risk allergen conflicts remove candidate from result`() = runBlocking {
        val safeFood = food(
            id = "safe_food",
            name = "Safe food",
            category = "protein",
            isCustom = true
        )
        val riskyFood = food(
            id = "risky_food",
            name = "Risky food",
            category = "protein",
            isCustom = true
        )
        val allergenRepository = FakeAllergenRepository().apply {
            profilesByFoodId = mapOf(
                "safe_food" to safetyProfileWithWarning(inferredConflict("gluten")),
                "risky_food" to safetyProfileWithWarning(manualConflict("milk"))
            )
        }
        val useCase = buildUseCase(
            foodRepository = FakeFoodRepository(listOf(safeFood, riskyFood)),
            mealRepository = FakeMealRepository(),
            userRepository = FakeUserRepository(user()),
            allergenRepository = allergenRepository
        )

        val result = useCase(limit = 5)

        assertEquals(listOf("safe_food"), result.map { it.food.id })
    }

    @Test
    fun `name inferred conflicts stay in results but receive penalty`() = runBlocking {
        val safeFood = food(
            id = "safe",
            name = "Safe",
            category = "fruit",
            isCustom = true
        )
        val inferredFood = food(
            id = "warning",
            name = "Warning",
            category = "grain",
            isCustom = true
        )
        val allergenRepository = FakeAllergenRepository().apply {
            profilesByFoodId = mapOf(
                "warning" to safetyProfileWithWarning(inferredConflict("gluten"))
            )
        }
        val useCase = buildUseCase(
            foodRepository = FakeFoodRepository(listOf(safeFood, inferredFood)),
            mealRepository = FakeMealRepository(),
            userRepository = FakeUserRepository(user(goal = UserGoal.MAINTAIN_WEIGHT)),
            allergenRepository = allergenRepository
        )

        val result = useCase(limit = 5)

        assertEquals(setOf("safe", "warning"), result.map { it.food.id }.toSet())
        val warning = result.first { it.food.id == "warning" }
        assertEquals(18, warning.breakdown.safetyPenalty)
    }

    private fun buildUseCase(
        foodRepository: FakeFoodRepository,
        mealRepository: FakeMealRepository,
        userRepository: FakeUserRepository,
        favoriteFoodsRepository: FakeFavoriteFoodsRepository = FakeFavoriteFoodsRepository(),
        allergenRepository: FakeAllergenRepository = FakeAllergenRepository(),
        dailyNutritionUseCase: GetDailyNutritionUseCase = GetDailyNutritionUseCase(mealRepository, foodRepository)
    ): GetPersonalizedFoodRecommendationsUseCase {
        return GetPersonalizedFoodRecommendationsUseCase(
            foodRepository = foodRepository,
            mealRepository = mealRepository,
            userRepository = userRepository,
            favoriteFoodsRepository = favoriteFoodsRepository,
            allergenRepository = allergenRepository,
            nutritionTargetsResolver = {
                NutritionTargets(
                    maintenanceCalories = 2400,
                    targetCalories = 2700,
                    proteinGrams = 180,
                    fatGrams = 80,
                    carbsGrams = 280,
                    proteinGoalBasis = ProteinGoalBasis.TOTAL_BODY_WEIGHT,
                    proteinReferenceWeightKg = 80.0
                )
            },
            getDailyNutritionUseCase = dailyNutritionUseCase
        )
    }
}
