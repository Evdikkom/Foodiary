package com.example.foodiary.domain.usecase

import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodRecommendation
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.RecommendationScoreBreakdown
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.repository.AllergenRepository
import com.example.foodiary.domain.repository.FavoriteFoodsRepository
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.repository.MealRepository
import com.example.foodiary.domain.repository.UserRepository
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class GetPersonalizedFoodRecommendationsUseCase(
    private val foodRepository: FoodRepository,
    private val mealRepository: MealRepository,
    private val userRepository: UserRepository,
    private val favoriteFoodsRepository: FavoriteFoodsRepository,
    private val allergenRepository: AllergenRepository,
    private val nutritionTargetsResolver: (User) -> NutritionTargets,
    private val getDailyNutritionUseCase: GetDailyNutritionUseCase
) {

    suspend operator fun invoke(
        mealType: MealType? = null,
        limit: Int
    ): List<FoodRecommendation> {
        val now = System.currentTimeMillis()
        val historyStart = now - HISTORY_WINDOW_MS
        val recentMeals = mealRepository.getMealsForPeriod(historyStart, now + 1)
        val favorites = favoriteFoodsRepository.getFavoriteFoodIds()
        val customFoods = foodRepository.getCustomFoods(CUSTOM_CANDIDATES_LIMIT)
        val user = userRepository.getCurrentUser()
        val hasBehaviorSignals = recentMeals.isNotEmpty() || favorites.isNotEmpty() || customFoods.isNotEmpty()

        if (!hasBehaviorSignals && user == null) return emptyList()

        val seedIds = if (mealType != null) {
            seedIdsForMealType(mealType)
        } else {
            generalSeedIds()
        }

        val candidateIds = linkedSetOf<String>().apply {
            if (hasBehaviorSignals) {
                addAll(topHistoryIds(recentMeals, mealType, HISTORY_CANDIDATES_LIMIT))
                if (mealType != null) {
                    addAll(topMealTypeIds(recentMeals, mealType, MEAL_TYPE_CANDIDATES_LIMIT))
                }
                addAll(favorites)
            }
            addAll(seedIds)
        }
        candidateIds.addAll(customFoods.map { it.id })

        val candidateFoods = (foodRepository.getFoodsByIds(candidateIds.toList()) + customFoods)
            .distinctBy { it.id }
        if (candidateFoods.isEmpty()) return emptyList()

        val nutritionTargets = user?.let(nutritionTargetsResolver)
        val dailyNutrition = if (user != null) {
            val (startOfDay, endOfDay) = todayBounds()
            getDailyNutritionUseCase(startOfDay, endOfDay)
        } else {
            null
        }

        val safetyProfiles = allergenRepository.getFoodSafetyProfiles(candidateFoods)
        val filteredFoods = candidateFoods.filterNot { food ->
            val profile = safetyProfiles[food.id] ?: return@filterNot false
            (profile.highRiskConflicts + profile.warningConflicts)
                .any { it.evidenceType != AllergenEvidenceType.NAME_MATCH_INFERRED }
        }
        if (filteredFoods.isEmpty()) return emptyList()

        val mealsByFoodId = recentMeals.groupBy { it.foodId }
        val maxHistoryWeight = filteredFoods.maxOfOrNull { food ->
            weightedHistory(mealsByFoodId[food.id].orEmpty(), mealType, now)
        }?.takeIf { it > 0.0 } ?: 1.0

        val recommendations = filteredFoods.map { food ->
            val mealsForFood = mealsByFoodId[food.id].orEmpty()
            val effectiveMealType = mealType ?: resolveCurrentMealType(now)
            val historyScore = ((weightedHistory(mealsForFood, mealType, now) / maxHistoryWeight) * 100)
                .roundToInt()
                .coerceIn(0, 100)
            val preferenceScore = computePreferenceScore(
                food = food,
                favoriteIds = favorites,
                seedIds = seedIds.toSet()
            )
            val goalFitScore = computeGoalFitScore(food, user)
            val macroGapScore = computeMacroGapScore(food, user, nutritionTargets, dailyNutrition)
            val varietyScore = computeVarietyScore(mealsForFood, now)
            val portionPracticalityScore = computePortionPracticalityScore(
                food = food,
                targets = nutritionTargets,
                dailyNutrition = dailyNutrition,
                mealType = effectiveMealType
            )
            val mealTimingScore = computeMealTimingScore(
                food = food,
                mealType = effectiveMealType
            )
            val roleBalanceScore = computeRoleBalanceScore(food, mealsForFood, now)
            val confidenceScore = computeConfidenceScore(
                food = food,
                user = user,
                recentMeals = recentMeals,
                mealsForFood = mealsForFood,
                favoriteIds = favorites,
                seedIds = seedIds.toSet(),
                customFoods = customFoods,
                dailyNutrition = dailyNutrition
            )
            val safetyPenalty = computeSafetyPenalty(food.id, safetyProfiles)

            val totalScore = (
                historyScore * HISTORY_WEIGHT +
                    preferenceScore * PREFERENCE_WEIGHT +
                    goalFitScore * GOAL_WEIGHT +
                    macroGapScore * MACRO_GAP_WEIGHT +
                    varietyScore * VARIETY_WEIGHT +
                    portionPracticalityScore * PORTION_WEIGHT +
                    mealTimingScore * TIMING_WEIGHT +
                    roleBalanceScore * ROLE_WEIGHT +
                    confidenceScore * CONFIDENCE_WEIGHT -
                    safetyPenalty
                )
                .roundToInt()
                .coerceIn(0, 100)

            val primaryReason = buildPrimaryReason(
                food = food,
                mealType = mealType,
                mealsForFood = mealsForFood,
                favoriteIds = favorites,
                seedIds = seedIds.toSet(),
                user = user,
                nutritionTargets = nutritionTargets,
                dailyNutrition = dailyNutrition,
                portionPracticalityScore = portionPracticalityScore,
                mealTimingScore = mealTimingScore,
                effectiveMealType = effectiveMealType
            )
            val secondaryReason = buildSecondaryReason(
                food = food,
                historyScore = historyScore,
                preferenceScore = preferenceScore,
                goalFitScore = goalFitScore,
                macroGapScore = macroGapScore,
                varietyScore = varietyScore,
                portionPracticalityScore = portionPracticalityScore,
                mealTimingScore = mealTimingScore,
                confidenceScore = confidenceScore,
                favoriteIds = favorites,
                mealType = mealType,
                mealsForFood = mealsForFood,
                user = user,
                nutritionTargets = nutritionTargets,
                dailyNutrition = dailyNutrition,
                primaryReason = primaryReason
            )

            FoodRecommendation(
                food = food,
                totalScore = totalScore,
                primaryReason = primaryReason,
                secondaryReason = secondaryReason,
                breakdown = RecommendationScoreBreakdown(
                    historyScore = historyScore,
                    preferenceScore = preferenceScore,
                    goalFitScore = goalFitScore,
                    macroGapScore = macroGapScore,
                    varietyScore = varietyScore,
                    safetyPenalty = safetyPenalty,
                    confidenceScore = confidenceScore,
                    portionPracticalityScore = portionPracticalityScore,
                    mealTimingScore = mealTimingScore,
                    roleBalanceScore = roleBalanceScore
                )
            )
        }

        return recommendations
            .sortedWith(
                compareByDescending<FoodRecommendation> { it.totalScore }
                    .thenByDescending { it.breakdown.macroGapScore }
                    .thenByDescending { it.breakdown.goalFitScore }
                    .thenByDescending { it.breakdown.historyScore }
                    .thenBy { it.food.name.lowercase(Locale.getDefault()) }
            )
            .take(limit)
    }

    private fun topHistoryIds(
        meals: List<Meal>,
        mealType: MealType?,
        limit: Int
    ): List<String> {
        val now = System.currentTimeMillis()
        return meals
            .groupBy { it.foodId }
            .mapValues { (_, foodMeals) ->
                weightedHistory(foodMeals, mealType, now)
            }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    private fun topMealTypeIds(
        meals: List<Meal>,
        mealType: MealType,
        limit: Int
    ): List<String> {
        val now = System.currentTimeMillis()
        return meals
            .filter { it.mealType == mealType }
            .groupBy { it.foodId }
            .mapValues { (_, foodMeals) ->
                weightedHistory(foodMeals, mealType, now)
            }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    private fun weightedHistory(
        meals: List<Meal>,
        selectedMealType: MealType?,
        now: Long
    ): Double {
        return meals.sumOf { meal ->
            val daysAgo = ((now - meal.timestamp) / DAY_MS).coerceAtLeast(0)
            val ageWeight = when {
                daysAgo <= 3 -> 1.0
                daysAgo <= 7 -> 0.82
                daysAgo <= 14 -> 0.62
                daysAgo <= 30 -> 0.42
                else -> 0.24
            }
            val mealTypeMultiplier = if (
                selectedMealType != null && meal.mealType == selectedMealType
            ) {
                1.2
            } else {
                1.0
            }
            ageWeight * mealTypeMultiplier
        }
    }

    private fun computePreferenceScore(
        food: Food,
        favoriteIds: Set<String>,
        seedIds: Set<String>
    ): Int {
        var score = 0
        if (food.id in favoriteIds) score += 36
        if (food.isCustom) score += if (food.category == "custom_recipe") 20 else 16
        if (food.id in seedIds) score += 10
        if (food.category.contains("fruit") || food.category.contains("vegetable")) score += 6
        return score.coerceIn(0, 100)
    }

    private fun computeGoalFitScore(
        food: Food,
        user: User?
    ): Int {
        val goal = user?.goal ?: UserGoal.MAINTAIN_WEIGHT
        val proteinNorm = normalize(food.proteinPer100g, 0.0, 30.0)
        val carbsNorm = normalize(food.carbsPer100g, 0.0, 45.0)
        val fatNorm = normalize(food.fatPer100g, 0.0, 22.0)
        val caloriesNorm = normalize(food.caloriesPer100g, 40.0, 360.0)
        val leanCaloriesNorm = inverseNormalize(food.caloriesPer100g, 50.0, 360.0)
        val moderateCaloriesNorm = (1.0 - (abs(food.caloriesPer100g - 180.0) / 180.0))
            .coerceIn(0.0, 1.0)
        val produceBonus = if (
            food.category.contains("fruit") || food.category.contains("vegetable")
        ) {
            1.0
        } else {
            0.0
        }

        val score = when (goal) {
            UserGoal.WEIGHT_LOSS ->
                0.50 * proteinNorm + 0.35 * leanCaloriesNorm + 0.15 * produceBonus

            UserGoal.MAINTAIN_WEIGHT ->
                0.30 * proteinNorm + 0.35 * moderateCaloriesNorm + 0.20 * produceBonus + 0.15 * carbsNorm

            UserGoal.WEIGHT_GAIN ->
                0.45 * caloriesNorm + 0.30 * proteinNorm + 0.25 * carbsNorm

            UserGoal.MUSCLE_GAIN_TRAINING ->
                0.45 * proteinNorm + 0.30 * carbsNorm + 0.20 * caloriesNorm + 0.05 * inverseNormalize(fatNorm, 0.0, 1.0)
        }

        return (score * 100).roundToInt().coerceIn(0, 100)
    }

    private fun computeMacroGapScore(
        food: Food,
        user: User?,
        targets: NutritionTargets?,
        dailyNutrition: com.example.foodiary.domain.model.DailyNutrition?
    ): Int {
        if (user == null || targets == null || dailyNutrition == null) return 50

        val proteinNeed = positiveRatio(targets.proteinGrams - dailyNutrition.totalProtein, targets.proteinGrams.toDouble())
        val fatNeed = positiveRatio(targets.fatGrams - dailyNutrition.totalFat, targets.fatGrams.toDouble())
        val carbsNeed = positiveRatio(targets.carbsGrams - dailyNutrition.totalCarbs, targets.carbsGrams.toDouble())
        val caloriesNeed = positiveRatio(
            targets.targetCalories - dailyNutrition.totalCalories,
            targets.targetCalories.toDouble()
        )

        val proteinNorm = normalize(food.proteinPer100g, 0.0, 30.0)
        val fatNorm = normalize(food.fatPer100g, 0.0, 22.0)
        val carbsNorm = normalize(food.carbsPer100g, 0.0, 45.0)
        val calorieNorm = normalize(food.caloriesPer100g, 40.0, 360.0)
        val leanCaloriesNorm = inverseNormalize(food.caloriesPer100g, 50.0, 360.0)
        val moderateCaloriesNorm = (1.0 - (abs(food.caloriesPer100g - 180.0) / 180.0))
            .coerceIn(0.0, 1.0)

        val macroNeedSum = proteinNeed + fatNeed + carbsNeed
        val macroAlignment = if (macroNeedSum > 0.0) {
            (
                proteinNeed * proteinNorm +
                    fatNeed * fatNorm +
                    carbsNeed * carbsNorm
                ) / macroNeedSum
        } else {
            0.5
        }

        val calorieAlignment = when {
            caloriesNeed > 0.35 -> calorieNorm
            caloriesNeed > 0.10 -> 0.5 + calorieNorm * 0.5
            user.goal == UserGoal.WEIGHT_LOSS -> leanCaloriesNorm
            else -> moderateCaloriesNorm
        }

        val score = 0.65 * macroAlignment + 0.35 * calorieAlignment
        return (score * 100).roundToInt().coerceIn(0, 100)
    }

    private fun computeVarietyScore(
        meals: List<Meal>,
        now: Long
    ): Int {
        val lastMealTimestamp = meals.maxOfOrNull { it.timestamp } ?: return 85
        val daysSinceLast = ((now - lastMealTimestamp) / DAY_MS).coerceAtLeast(0)
        return when {
            daysSinceLast == 0L -> 10
            daysSinceLast == 1L -> 25
            daysSinceLast <= 3L -> 45
            daysSinceLast <= 6L -> 65
            daysSinceLast <= 13L -> 82
            else -> 100
        }
    }

    private fun computeSafetyPenalty(
        foodId: String,
        safetyProfiles: Map<String, com.example.foodiary.domain.model.FoodSafetyProfile>
    ): Int {
        val profile = safetyProfiles[foodId] ?: return 0
        return if (
            (profile.highRiskConflicts + profile.warningConflicts)
                .any { it.evidenceType == AllergenEvidenceType.NAME_MATCH_INFERRED }
        ) {
            18
        } else {
            0
        }
    }

    private fun computePortionPracticalityScore(
        food: Food,
        targets: NutritionTargets?,
        dailyNutrition: com.example.foodiary.domain.model.DailyNutrition?,
        mealType: MealType
    ): Int {
        val role = foodRole(food)
        var score = when (role) {
            FoodRole.PROTEIN_MAIN,
            FoodRole.LIGHT_PRODUCE,
            FoodRole.DAIRY_SNACK,
            FoodRole.FRUIT_SNACK -> 78
            FoodRole.GRAIN_SIDE,
            FoodRole.CUSTOM_RECIPE -> 70
            FoodRole.FAT_SNACK -> 58
            FoodRole.SWEET_SNACK -> 46
            FoodRole.BEVERAGE -> 52
            FoodRole.SAUCE_OR_EXTRA -> 38
            FoodRole.OTHER -> 62
        }

        if (targets != null && dailyNutrition != null) {
            val share = mealShare(mealType)
            val proteinNeed = ((targets.proteinGrams - dailyNutrition.totalProtein).coerceAtLeast(0.0)) * share
            val fatNeed = ((targets.fatGrams - dailyNutrition.totalFat).coerceAtLeast(0.0)) * share
            val carbsNeed = ((targets.carbsGrams - dailyNutrition.totalCarbs).coerceAtLeast(0.0)) * share
            val calorieNeed = ((targets.targetCalories - dailyNutrition.totalCalories).coerceAtLeast(0.0)) * share
            val dominantNeed = listOf(
                MacroNeed("protein", proteinNeed, targets.proteinGrams * share),
                MacroNeed("fat", fatNeed, targets.fatGrams * share),
                MacroNeed("carbs", carbsNeed, targets.carbsGrams * share),
                MacroNeed("calories", calorieNeed, targets.targetCalories * share)
            ).maxByOrNull { positiveRatio(it.amount, it.reference.coerceAtLeast(1.0)) }

            val estimatedPortionGrams = when (dominantNeed?.name) {
                "protein" -> gramsForNeed(proteinNeed, food.proteinPer100g)
                "fat" -> gramsForNeed(fatNeed, food.fatPer100g)
                "carbs" -> gramsForNeed(carbsNeed, food.carbsPer100g)
                else -> gramsForNeed(calorieNeed, food.caloriesPer100g)
            }

            val range = practicalRange(role, mealType)
            if (estimatedPortionGrams != null) {
                score = when {
                    estimatedPortionGrams in range.minGrams..range.maxGrams -> 92
                    estimatedPortionGrams < range.minGrams ->
                        (74 - ((range.minGrams - estimatedPortionGrams) / range.minGrams * 28)).roundToInt()
                    else ->
                        (84 - ((estimatedPortionGrams - range.maxGrams) / range.maxGrams * 42)).roundToInt()
                }
            }
        }

        if (food.caloriesPer100g >= 450.0 || food.fatPer100g >= 25.0) score -= 12
        if (food.proteinPer100g >= 12.0 && food.caloriesPer100g <= 240.0) score += 8
        if (food.carbsPer100g >= 45.0 && mealType == MealType.LATE_DINNER) score -= 12
        return score.coerceIn(20, 100)
    }

    private fun computeMealTimingScore(
        food: Food,
        mealType: MealType
    ): Int {
        val role = foodRole(food)
        var score = when (mealType) {
            MealType.BREAKFAST -> when (role) {
                FoodRole.GRAIN_SIDE,
                FoodRole.FRUIT_SNACK,
                FoodRole.DAIRY_SNACK,
                FoodRole.PROTEIN_MAIN -> 86
                FoodRole.FAT_SNACK -> 68
                FoodRole.LIGHT_PRODUCE,
                FoodRole.CUSTOM_RECIPE -> 64
                FoodRole.SWEET_SNACK -> 45
                FoodRole.BEVERAGE -> 42
                FoodRole.SAUCE_OR_EXTRA -> 30
                FoodRole.OTHER -> 60
            }
            MealType.LUNCH -> when (role) {
                FoodRole.PROTEIN_MAIN,
                FoodRole.GRAIN_SIDE,
                FoodRole.LIGHT_PRODUCE,
                FoodRole.CUSTOM_RECIPE -> 88
                FoodRole.DAIRY_SNACK,
                FoodRole.FRUIT_SNACK -> 66
                FoodRole.FAT_SNACK -> 56
                FoodRole.SWEET_SNACK -> 38
                FoodRole.BEVERAGE -> 42
                FoodRole.SAUCE_OR_EXTRA -> 34
                FoodRole.OTHER -> 62
            }
            MealType.DINNER -> when (role) {
                FoodRole.PROTEIN_MAIN,
                FoodRole.LIGHT_PRODUCE,
                FoodRole.DAIRY_SNACK,
                FoodRole.CUSTOM_RECIPE -> 86
                FoodRole.GRAIN_SIDE -> 66
                FoodRole.FRUIT_SNACK -> 60
                FoodRole.FAT_SNACK -> 52
                FoodRole.SWEET_SNACK -> 34
                FoodRole.BEVERAGE -> 40
                FoodRole.SAUCE_OR_EXTRA -> 32
                FoodRole.OTHER -> 58
            }
            MealType.SNACK,
            MealType.AFTERNOON_SNACK -> when (role) {
                FoodRole.FRUIT_SNACK,
                FoodRole.DAIRY_SNACK,
                FoodRole.FAT_SNACK -> 86
                FoodRole.PROTEIN_MAIN,
                FoodRole.LIGHT_PRODUCE -> 72
                FoodRole.GRAIN_SIDE -> 64
                FoodRole.SWEET_SNACK -> 52
                FoodRole.BEVERAGE -> 46
                FoodRole.SAUCE_OR_EXTRA -> 26
                FoodRole.CUSTOM_RECIPE,
                FoodRole.OTHER -> 56
            }
            MealType.LATE_DINNER -> when (role) {
                FoodRole.DAIRY_SNACK,
                FoodRole.PROTEIN_MAIN,
                FoodRole.LIGHT_PRODUCE -> 86
                FoodRole.FRUIT_SNACK -> 58
                FoodRole.GRAIN_SIDE -> 44
                FoodRole.FAT_SNACK,
                FoodRole.SWEET_SNACK -> 30
                FoodRole.BEVERAGE -> 38
                FoodRole.SAUCE_OR_EXTRA -> 24
                FoodRole.CUSTOM_RECIPE,
                FoodRole.OTHER -> 52
            }
        }

        if (food.caloriesPer100g > 380.0 && mealType in setOf(MealType.SNACK, MealType.AFTERNOON_SNACK, MealType.LATE_DINNER)) {
            score -= 16
        }
        if (food.fatPer100g > 20.0 && mealType in setOf(MealType.DINNER, MealType.LATE_DINNER)) {
            score -= 12
        }
        if (food.proteinPer100g >= 12.0 && mealType in setOf(MealType.DINNER, MealType.LATE_DINNER)) {
            score += 6
        }
        return score.coerceIn(0, 100)
    }

    private fun computeRoleBalanceScore(
        food: Food,
        mealsForFood: List<Meal>,
        now: Long
    ): Int {
        val roleBase = when (foodRole(food)) {
            FoodRole.PROTEIN_MAIN,
            FoodRole.LIGHT_PRODUCE,
            FoodRole.DAIRY_SNACK,
            FoodRole.FRUIT_SNACK -> 86
            FoodRole.GRAIN_SIDE,
            FoodRole.CUSTOM_RECIPE -> 74
            FoodRole.FAT_SNACK -> 62
            FoodRole.BEVERAGE -> 50
            FoodRole.SWEET_SNACK -> 44
            FoodRole.SAUCE_OR_EXTRA -> 34
            FoodRole.OTHER -> 58
        }
        val lastMealTimestamp = mealsForFood.maxOfOrNull { it.timestamp }
        val recencyPenalty = if (lastMealTimestamp == null) {
            0
        } else {
            when (((now - lastMealTimestamp) / DAY_MS).coerceAtLeast(0)) {
                0L -> 34
                1L -> 18
                in 2L..3L -> 10
                else -> 0
            }
        }
        return (roleBase - recencyPenalty).coerceIn(0, 100)
    }

    private fun computeConfidenceScore(
        food: Food,
        user: User?,
        recentMeals: List<Meal>,
        mealsForFood: List<Meal>,
        favoriteIds: Set<String>,
        seedIds: Set<String>,
        customFoods: List<Food>,
        dailyNutrition: com.example.foodiary.domain.model.DailyNutrition?
    ): Int {
        var score = if (user != null) 30 else 15
        if ((dailyNutrition?.mealsCount ?: 0) > 0) score += 16
        score += (recentMeals.size * 2).coerceAtMost(22)
        if (food.id in favoriteIds) score += 12
        if (food.id in seedIds) score += 8
        if (food.isCustom || customFoods.any { it.id == food.id }) score += 10
        if (mealsForFood.isNotEmpty()) score += 10
        return score.coerceIn(20, 100)
    }

    private fun buildPrimaryReason(
        food: Food,
        mealType: MealType?,
        mealsForFood: List<Meal>,
        favoriteIds: Set<String>,
        seedIds: Set<String>,
        user: User?,
        nutritionTargets: NutritionTargets?,
        dailyNutrition: com.example.foodiary.domain.model.DailyNutrition?,
        portionPracticalityScore: Int,
        mealTimingScore: Int,
        effectiveMealType: MealType
    ): String {
        buildMacroReason(food, nutritionTargets, dailyNutrition)?.let { return it }
        if (portionPracticalityScore >= 82 && mealTimingScore >= 76) {
            return "Порция реалистична и хорошо подходит для ${mealTypeLabelForPurpose(effectiveMealType)}"
        }
        buildGoalReason(food, user)?.let { return it }

        if (mealType != null) {
            val mealTypeCount = mealsForFood.count { it.mealType == mealType }
            if (mealTypeCount >= 2) {
                return "Часто выбираете на ${mealTypeLabelForSlot(mealType)}"
            }
        } else if (mealsForFood.size >= 3) {
            return "Часто возвращаетесь к этому продукту"
        }

        if (food.id in favoriteIds) {
            return "Есть в избранном и хорошо вписывается в текущий день"
        }

        if (food.id in seedIds) {
            return if (mealType != null) {
                "Хорошо подходит для ${mealTypeLabelForPurpose(mealType)}"
            } else {
                "Подходит под текущую цель и дневной баланс"
            }
        }

        if (food.isCustom && food.category == "custom_recipe") {
            return "Ваше готовое блюдо под текущую цель"
        }

        if (food.isCustom) {
            return "Ваш сохранённый продукт, который удобно использовать снова"
        }

        return "Подобрано по вашей цели и текущему балансу КБЖУ"
    }

    private fun buildSecondaryReason(
        food: Food,
        historyScore: Int,
        preferenceScore: Int,
        goalFitScore: Int,
        macroGapScore: Int,
        varietyScore: Int,
        portionPracticalityScore: Int,
        mealTimingScore: Int,
        confidenceScore: Int,
        favoriteIds: Set<String>,
        mealType: MealType?,
        mealsForFood: List<Meal>,
        user: User?,
        nutritionTargets: NutritionTargets?,
        dailyNutrition: com.example.foodiary.domain.model.DailyNutrition?,
        primaryReason: String
    ): String? {
        val reasons = buildList {
            if (macroGapScore >= 60) {
                buildMacroReason(food, nutritionTargets, dailyNutrition)?.let(::add)
            }

            if (goalFitScore >= 65) {
                buildGoalReason(food, user)?.let(::add)
            }

            if (portionPracticalityScore >= 78) {
                add("Подходит по размеру порции без искусственного завышения граммовки")
            } else if (portionPracticalityScore <= 45) {
                add("Лучше выбирать небольшую порцию: продукт плотный по калориям или жирам")
            }

            if (mealTimingScore >= 78) {
                add("Уместно для текущего времени приема пищи")
            } else if (mealTimingScore <= 45) {
                add("Лучше оставить для другого приема пищи")
            }

            if (mealType != null && mealsForFood.any { it.mealType == mealType } && historyScore >= 60) {
                add("Вы уже выбирали это для ${mealTypeLabelForPurpose(mealType)}")
            } else if (mealType == null && mealsForFood.size >= 2 && historyScore >= 60) {
                add("Вы часто выбираете этот продукт")
            }

            if (food.id in favoriteIds && preferenceScore >= 50) {
                add("Есть в вашем избранном")
            } else if (food.isCustom && preferenceScore >= 45) {
                add("Основано на ваших сохранённых продуктах")
            }

            if (varietyScore >= 80) {
                add("Добавит разнообразия в рацион")
            }

            if (confidenceScore < 45) {
                add("Оценка предварительная: системе нужно больше записей дневника")
            }
        }

        return reasons.firstOrNull { it != primaryReason }
    }

    private fun buildGoalReason(
        food: Food,
        user: User?
    ): String? {
        return when (user?.goal ?: UserGoal.MAINTAIN_WEIGHT) {
            UserGoal.WEIGHT_LOSS ->
                if (food.proteinPer100g >= 10.0 || food.caloriesPer100g <= 140.0) {
                    "Подходит для цели снижения массы"
                } else {
                    null
                }

            UserGoal.MAINTAIN_WEIGHT ->
                if (food.caloriesPer100g in 90.0..260.0) {
                    "Хорошо вписывается в поддержание массы"
                } else {
                    null
                }

            UserGoal.WEIGHT_GAIN ->
                if (food.caloriesPer100g >= 180.0) {
                    "Помогает набрать калории без лишней мороки"
                } else {
                    null
                }

            UserGoal.MUSCLE_GAIN_TRAINING ->
                if (food.proteinPer100g >= 12.0 || food.carbsPer100g >= 20.0) {
                    "Подходит для набора мышечной массы"
                } else {
                    null
                }
        }
    }

    private fun buildMacroReason(
        food: Food,
        nutritionTargets: NutritionTargets?,
        dailyNutrition: com.example.foodiary.domain.model.DailyNutrition?
    ): String? {
        if (nutritionTargets == null || dailyNutrition == null) return null

        val proteinNeed = nutritionTargets.proteinGrams - dailyNutrition.totalProtein
        val fatNeed = nutritionTargets.fatGrams - dailyNutrition.totalFat
        val carbsNeed = nutritionTargets.carbsGrams - dailyNutrition.totalCarbs

        val dominantNeed = listOf(
            "protein" to proteinNeed,
            "fat" to fatNeed,
            "carbs" to carbsNeed
        ).maxByOrNull { it.second } ?: return null

        if (dominantNeed.second <= 0.0) return null

        return when (dominantNeed.first) {
            "protein" ->
                if (food.proteinPer100g >= 10.0) "Поможет добрать белок сегодня" else null
            "fat" ->
                if (food.fatPer100g >= 8.0) "Поможет добрать жиры сегодня" else null
            "carbs" ->
                if (food.carbsPer100g >= 15.0) "Поможет добрать углеводы сегодня" else null
            else -> null
        }
    }

    private fun gramsForNeed(
        need: Double,
        per100g: Double
    ): Double? {
        if (need <= 0.0 || per100g <= 0.0) return null
        return need / per100g * 100.0
    }

    private fun mealShare(type: MealType): Double {
        return when (type) {
            MealType.BREAKFAST -> 0.24
            MealType.LUNCH -> 0.32
            MealType.DINNER -> 0.28
            MealType.SNACK,
            MealType.AFTERNOON_SNACK -> 0.14
            MealType.LATE_DINNER -> 0.10
        }
    }

    private fun practicalRange(
        role: FoodRole,
        mealType: MealType
    ): PracticalRange {
        val base = when (role) {
            FoodRole.PROTEIN_MAIN -> PracticalRange(80.0, 230.0)
            FoodRole.GRAIN_SIDE -> PracticalRange(70.0, 240.0)
            FoodRole.LIGHT_PRODUCE -> PracticalRange(120.0, 420.0)
            FoodRole.FRUIT_SNACK -> PracticalRange(90.0, 300.0)
            FoodRole.DAIRY_SNACK -> PracticalRange(100.0, 280.0)
            FoodRole.FAT_SNACK -> PracticalRange(15.0, 50.0)
            FoodRole.SWEET_SNACK -> PracticalRange(15.0, 70.0)
            FoodRole.BEVERAGE -> PracticalRange(150.0, 350.0)
            FoodRole.SAUCE_OR_EXTRA -> PracticalRange(5.0, 35.0)
            FoodRole.CUSTOM_RECIPE -> PracticalRange(150.0, 450.0)
            FoodRole.OTHER -> PracticalRange(50.0, 250.0)
        }
        return when (mealType) {
            MealType.SNACK,
            MealType.AFTERNOON_SNACK -> PracticalRange(base.minGrams * 0.70, base.maxGrams * 0.72)
            MealType.LATE_DINNER -> PracticalRange(base.minGrams * 0.60, base.maxGrams * 0.62)
            else -> base
        }
    }

    private fun foodRole(food: Food): FoodRole {
        val name = food.name.lowercase()
        val id = food.id.lowercase()
        val category = food.category.lowercase()
        val text = "$id $name $category"
        return when {
            food.isCustom && category == "custom_recipe" -> FoodRole.CUSTOM_RECIPE
            containsAny(text, sweetKeywords) -> FoodRole.SWEET_SNACK
            containsAny(text, beverageKeywords) -> FoodRole.BEVERAGE
            containsAny(text, sauceKeywords) -> FoodRole.SAUCE_OR_EXTRA
            category.contains("fruit") -> FoodRole.FRUIT_SNACK
            category.contains("dairy") && (food.proteinPer100g >= 6.0 || food.caloriesPer100g <= 180.0) ->
                FoodRole.DAIRY_SNACK
            category.contains("nuts") -> FoodRole.FAT_SNACK
            category.contains("vegetable") -> FoodRole.LIGHT_PRODUCE
            category.contains("grain") -> FoodRole.GRAIN_SIDE
            category.contains("protein") -> FoodRole.PROTEIN_MAIN
            food.proteinPer100g >= 15.0 -> FoodRole.PROTEIN_MAIN
            food.carbsPer100g >= 22.0 -> FoodRole.GRAIN_SIDE
            food.fatPer100g >= 18.0 -> FoodRole.FAT_SNACK
            else -> FoodRole.OTHER
        }
    }

    private fun containsAny(
        text: String,
        keywords: Set<String>
    ): Boolean {
        return keywords.any(text::contains)
    }

    private fun resolveCurrentMealType(now: Long): MealType {
        val hour = Calendar.getInstance().apply { timeInMillis = now }
            .get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> MealType.BREAKFAST
            in 11..14 -> MealType.LUNCH
            in 15..17 -> MealType.SNACK
            in 18..21 -> MealType.DINNER
            else -> MealType.LATE_DINNER
        }
    }

    private fun todayBounds(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return start to calendar.timeInMillis
    }

    private fun generalSeedIds(): List<String> {
        return MealType.values()
            .flatMap(::seedIdsForMealType)
            .distinct()
    }

    private fun seedIdsForMealType(type: MealType): List<String> {
        return when (type) {
            MealType.AFTERNOON_SNACK -> listOf(
                "apple",
                "banana",
                "pear",
                "orange",
                "mandarin",
                "strawberries",
                "blueberries",
                "greek_yogurt",
                "skyr",
                "almonds",
                "walnuts",
                "peanuts",
                "cottage_cheese",
                "cottage_cheese_lowfat",
                "berries_mix",
                "dried_apricots",
                "raisins",
                "dark_chocolate",
                "hummus",
                "carrot"
            )
            MealType.LATE_DINNER -> listOf(
                "kefir_2_5",
                "skyr",
                "cottage_cheese",
                "cottage_cheese_lowfat",
                "egg_white",
                "egg",
                "cod",
                "turkey_fillet",
                "chicken_breast",
                "broccoli",
                "cauliflower",
                "zucchini",
                "mushrooms",
                "cucumber",
                "tomato",
                "lettuce"
            )
            MealType.BREAKFAST -> listOf(
                "oatmeal",
                "oat_porridge",
                "banana",
                "apple",
                "berries_mix",
                "strawberries",
                "greek_yogurt",
                "skyr",
                "cottage_cheese",
                "cottage_cheese_lowfat",
                "egg",
                "egg_white",
                "wholegrain_bread",
                "rye_bread",
                "milk_2_5",
                "peanut_butter",
                "chia_seeds",
                "avocado"
            )
            MealType.LUNCH -> listOf(
                "chicken_breast",
                "turkey_fillet",
                "beef_lean",
                "pork_tenderloin",
                "rice",
                "brown_rice",
                "buckwheat",
                "quinoa",
                "bulgur",
                "pasta",
                "tomato",
                "cucumber",
                "broccoli",
                "bell_pepper",
                "salmon",
                "cod",
                "potato",
                "sweet_potato",
                "lentils",
                "chickpeas",
                "beans_red"
            )
            MealType.DINNER -> listOf(
                "salmon",
                "trout",
                "cod",
                "tuna",
                "turkey_fillet",
                "chicken_breast",
                "tofu",
                "lentils",
                "broccoli",
                "avocado",
                "cucumber",
                "tomato",
                "cauliflower",
                "zucchini",
                "eggplant",
                "mushrooms",
                "egg",
                "potato"
            )
            MealType.SNACK -> listOf(
                "apple",
                "banana",
                "pear",
                "orange",
                "kiwi",
                "grapes",
                "watermelon",
                "melon",
                "greek_yogurt",
                "kefir_2_5",
                "skyr",
                "almonds",
                "cashews",
                "pistachios",
                "cottage_cheese",
                "cottage_cheese_lowfat",
                "berries_mix",
                "hummus",
                "carrot",
                "dark_chocolate"
            )
        }
    }

    private fun mealTypeLabelForSlot(type: MealType): String {
        return when (type) {
            MealType.AFTERNOON_SNACK -> "полдник"
            MealType.LATE_DINNER -> "поздний ужин"
            MealType.BREAKFAST -> "завтрак"
            MealType.LUNCH -> "обед"
            MealType.DINNER -> "ужин"
            MealType.SNACK -> "перекус"
        }
    }

    private fun mealTypeLabelForPurpose(type: MealType): String {
        return when (type) {
            MealType.AFTERNOON_SNACK -> "полдника"
            MealType.LATE_DINNER -> "позднего ужина"
            MealType.BREAKFAST -> "завтрака"
            MealType.LUNCH -> "обеда"
            MealType.DINNER -> "ужина"
            MealType.SNACK -> "перекуса"
        }
    }

    private fun normalize(
        value: Double,
        min: Double,
        max: Double
    ): Double {
        if (max <= min) return 0.0
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun inverseNormalize(
        value: Double,
        min: Double,
        max: Double
    ): Double {
        return 1.0 - normalize(value, min, max)
    }

    private fun positiveRatio(
        value: Double,
        denominator: Double
    ): Double {
        if (denominator <= 0.0) return 0.0
        return (value.coerceAtLeast(0.0) / denominator).coerceIn(0.0, 1.0)
    }

    private data class MacroNeed(
        val name: String,
        val amount: Double,
        val reference: Double
    )

    private data class PracticalRange(
        val minGrams: Double,
        val maxGrams: Double
    )

    private enum class FoodRole {
        PROTEIN_MAIN,
        GRAIN_SIDE,
        LIGHT_PRODUCE,
        FRUIT_SNACK,
        DAIRY_SNACK,
        FAT_SNACK,
        SWEET_SNACK,
        BEVERAGE,
        SAUCE_OR_EXTRA,
        CUSTOM_RECIPE,
        OTHER
    }

    private companion object {
        const val HISTORY_WINDOW_MS = 60L * 24L * 60L * 60L * 1000L
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val HISTORY_CANDIDATES_LIMIT = 24
        const val MEAL_TYPE_CANDIDATES_LIMIT = 14
        const val CUSTOM_CANDIDATES_LIMIT = 20

        const val HISTORY_WEIGHT = 0.12
        const val PREFERENCE_WEIGHT = 0.10
        const val GOAL_WEIGHT = 0.17
        const val MACRO_GAP_WEIGHT = 0.22
        const val VARIETY_WEIGHT = 0.10
        const val PORTION_WEIGHT = 0.11
        const val TIMING_WEIGHT = 0.08
        const val ROLE_WEIGHT = 0.04
        const val CONFIDENCE_WEIGHT = 0.06

        val sweetKeywords = setOf(
            "chocolate",
            "dessert",
            "cookie",
            "cake",
            "candy",
            "sweet",
            "bar",
            "шоколад",
            "десерт",
            "печенье",
            "конфет",
            "слад"
        )
        val beverageKeywords = setOf(
            "juice",
            "soda",
            "cola",
            "coffee",
            "tea",
            "drink",
            "water",
            "сок",
            "газиров",
            "кофе",
            "чай",
            "напит",
            "вода"
        )
        val sauceKeywords = setOf(
            "sauce",
            "ketchup",
            "mayonnaise",
            "dressing",
            "соус",
            "кетчуп",
            "майонез",
            "заправ"
        )
    }
}
