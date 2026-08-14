package com.example.foodiary.domain.usecase

import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodRecommendation
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.ProteinGoalBasis
import com.example.foodiary.domain.model.RecommendationScoreBreakdown
import com.example.foodiary.domain.model.SmartCoachFocus
import com.example.foodiary.domain.model.WeatherFoodRecommendation
import com.example.foodiary.domain.model.WeatherNutritionFocus
import com.example.foodiary.domain.model.WeatherRecommendationAction
import com.example.foodiary.testing.FakeFoodRepository
import com.example.foodiary.testing.FakeMealRepository
import com.example.foodiary.testing.FakeUserRepository
import com.example.foodiary.testing.food
import com.example.foodiary.testing.meal
import com.example.foodiary.testing.user
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetSmartCoachInsightUseCaseTest {

    @Test
    fun `protein deficit selects protein recommendation and explains decision`() = runBlocking {
        val chicken = food(
            id = "chicken",
            name = "Chicken breast",
            caloriesPer100g = 165.0,
            proteinPer100g = 31.0,
            fatPer100g = 3.0,
            carbsPer100g = 0.0
        )
        val rice = food(
            id = "rice",
            name = "Rice",
            caloriesPer100g = 130.0,
            proteinPer100g = 2.7,
            fatPer100g = 0.3,
            carbsPer100g = 28.0
        )
        val useCase = buildUseCase(
            foods = listOf(chicken, rice),
            currentGoal = UserGoal.MUSCLE_GAIN_TRAINING
        )

        val result = useCase(
            dayStart = 0L,
            dailyNutrition = dailyNutrition(totalProtein = 32.0),
            recommendations = listOf(
                recommendation(chicken, macroGapScore = 92, goalFitScore = 88),
                recommendation(rice, macroGapScore = 45, goalFitScore = 54)
            ),
            weatherRecommendation = null,
            nowMillis = noonMillis()
        )

        assertNotNull(result)
        assertEquals(SmartCoachFocus.PROTEIN_DEFICIT, result?.focus)
        assertEquals("chicken", result?.suggestedFood?.id)
        assertTrue(result?.scoreDetails.orEmpty().size >= 4)
        assertTrue(result?.explanationBullets.orEmpty().any { it.contains("КБЖУ") })
    }

    @Test
    fun `weight loss keeps correction as forward looking product suggestion`() = runBlocking {
        val chocolate = food(
            id = "chocolate",
            name = "Chocolate bar",
            caloriesPer100g = 520.0,
            proteinPer100g = 6.0,
            fatPer100g = 31.0,
            carbsPer100g = 57.0,
            category = "grain"
        )
        val yogurt = food(
            id = "greek_yogurt",
            name = "Greek yogurt",
            caloriesPer100g = 95.0,
            proteinPer100g = 10.0,
            fatPer100g = 2.0,
            carbsPer100g = 4.0,
            category = "dairy"
        )
        val rice = food(
            id = "rice",
            name = "Rice",
            caloriesPer100g = 120.0,
            proteinPer100g = 3.0,
            fatPer100g = 0.4,
            carbsPer100g = 26.0,
            category = "grain"
        )
        val mealRepository = FakeMealRepository(
            meals = listOf(
                meal(
                    foodId = chocolate.id,
                    quantityInGrams = 90.0,
                    mealType = MealType.SNACK,
                    timestamp = 1_000L
                )
            )
        )
        val useCase = buildUseCase(
            foods = listOf(chocolate, yogurt, rice),
            mealRepository = mealRepository,
            currentGoal = UserGoal.WEIGHT_LOSS
        )

        val result = useCase(
            dayStart = 0L,
            dailyNutrition = dailyNutrition(totalCalories = 1_550.0, totalProtein = 42.0),
            recommendations = listOf(
                recommendation(rice, macroGapScore = 95, goalFitScore = 95),
                recommendation(yogurt, macroGapScore = 78, goalFitScore = 91)
            ),
            weatherRecommendation = null,
            nowMillis = noonMillis()
        )

        assertNull(result?.replacement)
        assertNotNull(result?.suggestedFood)
        assertTrue(result?.correctionMessage.orEmpty().contains("На 100 г"))
    }

    @Test
    fun `weather recommendation becomes smart coach weather context`() = runBlocking {
        val cucumber = food(
            id = "cucumber",
            name = "Cucumber",
            caloriesPer100g = 15.0,
            proteinPer100g = 0.7,
            fatPer100g = 0.1,
            carbsPer100g = 3.6
        )
        val useCase = buildUseCase(foods = listOf(cucumber))
        val weatherRecommendation = WeatherFoodRecommendation(
            title = "Weather",
            headline = "Light food",
            message = "Choose water-rich food.",
            buttonText = "Open",
            action = WeatherRecommendationAction.OPEN_FOOD,
            focus = WeatherNutritionFocus.HEAT_HYDRATION,
            food = cucumber
        )

        val result = useCase(
            dayStart = 0L,
            dailyNutrition = dailyNutrition(),
            recommendations = listOf(recommendation(cucumber)),
            weatherRecommendation = weatherRecommendation,
            nowMillis = noonMillis()
        )

        assertEquals("Погодный фактор: жара", result?.weatherContext?.title)
        assertEquals("cucumber", result?.weatherContext?.suggestedFood?.id)
    }

    @Test
    fun `smart coach builds day plan with combinations and user recipes`() = runBlocking {
        val chicken = food(
            id = "chicken",
            name = "Chicken breast",
            caloriesPer100g = 165.0,
            proteinPer100g = 31.0,
            fatPer100g = 3.0,
            carbsPer100g = 0.0,
            category = "protein"
        )
        val rice = food(
            id = "rice",
            name = "Rice",
            caloriesPer100g = 130.0,
            proteinPer100g = 2.7,
            fatPer100g = 0.3,
            carbsPer100g = 28.0,
            category = "grain"
        )
        val recipe = food(
            id = "recipe_bowl",
            name = "User protein bowl",
            caloriesPer100g = 180.0,
            proteinPer100g = 14.0,
            fatPer100g = 6.0,
            carbsPer100g = 18.0,
            isCustom = true,
            category = "custom_recipe"
        )
        val yogurt = food(
            id = "greek_yogurt",
            name = "Greek yogurt",
            caloriesPer100g = 95.0,
            proteinPer100g = 10.0,
            fatPer100g = 2.0,
            carbsPer100g = 4.0,
            category = "dairy"
        )
        val useCase = buildUseCase(
            foods = listOf(chicken, rice, recipe, yogurt),
            currentGoal = UserGoal.MUSCLE_GAIN_TRAINING
        )

        val result = useCase(
            dayStart = 0L,
            dailyNutrition = dailyNutrition(totalCalories = 620.0, totalProtein = 35.0),
            recommendations = listOf(
                recommendation(chicken, macroGapScore = 90, goalFitScore = 88),
                recommendation(rice, macroGapScore = 82, goalFitScore = 70),
                recommendation(recipe, macroGapScore = 86, goalFitScore = 84),
                recommendation(yogurt, macroGapScore = 74, goalFitScore = 82)
            ),
            weatherRecommendation = null,
            nowMillis = noonMillis()
        )

        val sections = result?.mealPlan?.sections.orEmpty()
        assertTrue(sections.isNotEmpty())
        assertTrue(sections.any { section -> section.options.any { it.items.size > 1 } })
        assertTrue(sections.any { section ->
            section.options.any { option -> option.items.any { it.food.id == recipe.id } }
        })
        assertTrue(sections.flatMap { it.options }.all { it.reason.first().isUpperCase() })
    }

    @Test
    fun `smart coach does not combine savory protein with breakfast grains`() = runBlocking {
        val beef = food(
            id = "beef",
            name = "Beef lean",
            caloriesPer100g = 190.0,
            proteinPer100g = 27.0,
            fatPer100g = 8.0,
            carbsPer100g = 0.0,
            category = "protein"
        )
        val oatmeal = food(
            id = "oatmeal",
            name = "Oatmeal flakes",
            caloriesPer100g = 360.0,
            proteinPer100g = 12.0,
            fatPer100g = 6.0,
            carbsPer100g = 60.0,
            category = "grain"
        )
        val buckwheat = food(
            id = "buckwheat",
            name = "Buckwheat",
            caloriesPer100g = 110.0,
            proteinPer100g = 3.6,
            fatPer100g = 1.0,
            carbsPer100g = 21.0,
            category = "grain"
        )
        val vegetables = food(
            id = "vegetables",
            name = "Vegetables",
            caloriesPer100g = 35.0,
            proteinPer100g = 2.0,
            fatPer100g = 0.2,
            carbsPer100g = 7.0,
            category = "vegetable"
        )
        val useCase = buildUseCase(
            foods = listOf(beef, oatmeal, buckwheat, vegetables),
            currentGoal = UserGoal.MUSCLE_GAIN_TRAINING
        )

        val result = useCase(
            dayStart = startOfTodayMillis(),
            dailyNutrition = dailyNutrition(totalCalories = 980.0, totalProtein = 42.0),
            recommendations = listOf(
                recommendation(beef, macroGapScore = 95, goalFitScore = 88),
                recommendation(oatmeal, macroGapScore = 92, goalFitScore = 84),
                recommendation(buckwheat, macroGapScore = 86, goalFitScore = 82),
                recommendation(vegetables, macroGapScore = 74, goalFitScore = 78)
            ),
            weatherRecommendation = null,
            nowMillis = eveningMillis()
        )

        val options = result?.mealPlan?.sections.orEmpty().flatMap { it.options }
        assertTrue(options.isNotEmpty())
        assertTrue(options.none { option ->
            val ids = option.items.map { it.food.id }.toSet()
            "beef" in ids && "oatmeal" in ids
        })
    }

    @Test
    fun `smart coach excludes foods already added today from suggestions`() = runBlocking {
        val chicken = food(
            id = "chicken",
            name = "Chicken breast",
            caloriesPer100g = 165.0,
            proteinPer100g = 31.0,
            fatPer100g = 3.0,
            carbsPer100g = 0.0,
            category = "protein"
        )
        val rice = food(
            id = "rice",
            name = "Rice",
            caloriesPer100g = 130.0,
            proteinPer100g = 2.7,
            fatPer100g = 0.3,
            carbsPer100g = 28.0,
            category = "grain"
        )
        val vegetables = food(
            id = "vegetables",
            name = "Vegetables",
            caloriesPer100g = 35.0,
            proteinPer100g = 2.0,
            fatPer100g = 0.2,
            carbsPer100g = 7.0,
            category = "vegetable"
        )
        val dayStart = startOfTodayMillis()
        val mealRepository = FakeMealRepository(
            meals = listOf(
                meal(
                    foodId = chicken.id,
                    quantityInGrams = 140.0,
                    mealType = MealType.LUNCH,
                    timestamp = dayStart + 12 * 60 * 60 * 1_000L
                )
            )
        )
        val useCase = buildUseCase(
            foods = listOf(chicken, rice, vegetables),
            mealRepository = mealRepository,
            currentGoal = UserGoal.MUSCLE_GAIN_TRAINING
        )

        val result = useCase(
            dayStart = dayStart,
            dailyNutrition = dailyNutrition(totalCalories = 1_050.0, totalProtein = 65.0),
            recommendations = listOf(
                recommendation(chicken, macroGapScore = 98, goalFitScore = 92),
                recommendation(rice, macroGapScore = 82, goalFitScore = 80),
                recommendation(vegetables, macroGapScore = 72, goalFitScore = 78)
            ),
            weatherRecommendation = null,
            nowMillis = eveningMillis()
        )

        assertTrue(result?.suggestedFood?.id != chicken.id)
        assertTrue(result?.mealPlan?.sections.orEmpty().flatMap { it.options }.all { option ->
            option.items.none { it.food.id == chicken.id }
        })
    }

    private fun buildUseCase(
        foods: List<Food>,
        mealRepository: FakeMealRepository = FakeMealRepository(),
        currentGoal: UserGoal = UserGoal.MAINTAIN_WEIGHT
    ): GetSmartCoachInsightUseCase {
        return GetSmartCoachInsightUseCase(
            foodRepository = FakeFoodRepository(foods),
            mealRepository = mealRepository,
            userRepository = FakeUserRepository(user(goal = currentGoal)),
            nutritionTargetsResolver = {
                NutritionTargets(
                    maintenanceCalories = 2_200,
                    targetCalories = 2_000,
                    proteinGrams = 130,
                    fatGrams = 65,
                    carbsGrams = 220,
                    proteinGoalBasis = ProteinGoalBasis.TOTAL_BODY_WEIGHT,
                    proteinReferenceWeightKg = 80.0
                )
            }
        )
    }

    private fun dailyNutrition(
        totalCalories: Double = 900.0,
        totalProtein: Double = 45.0,
        totalFat: Double = 28.0,
        totalCarbs: Double = 115.0
    ): DailyNutrition {
        return DailyNutrition(
            totalCalories = totalCalories,
            totalProtein = totalProtein,
            totalFat = totalFat,
            totalCarbs = totalCarbs,
            mealsCount = 2,
            mealsByType = mapOf(MealType.BREAKFAST to 1, MealType.LUNCH to 1)
        )
    }

    private fun recommendation(
        food: Food,
        macroGapScore: Int = 70,
        goalFitScore: Int = 70
    ): FoodRecommendation {
        return FoodRecommendation(
            food = food,
            totalScore = 80,
            primaryReason = "Reason",
            secondaryReason = null,
            breakdown = RecommendationScoreBreakdown(
                historyScore = 20,
                preferenceScore = 20,
                goalFitScore = goalFitScore,
                macroGapScore = macroGapScore,
                varietyScore = 80,
                safetyPenalty = 0
            )
        )
    }

    private fun noonMillis(): Long {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun eveningMillis(): Long {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 18)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfTodayMillis(): Long {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
