package com.example.foodiary.domain.usecase

import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodRecommendation
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.SmartCoachFocus
import com.example.foodiary.domain.model.SmartCoachForecast
import com.example.foodiary.domain.model.SmartCoachInsight
import com.example.foodiary.domain.model.SmartCoachMealPlan
import com.example.foodiary.domain.model.SmartCoachMealPlanItem
import com.example.foodiary.domain.model.SmartCoachMealPlanOption
import com.example.foodiary.domain.model.SmartCoachMealPlanSection
import com.example.foodiary.domain.model.SmartCoachReplacement
import com.example.foodiary.domain.model.SmartCoachScoreDetail
import com.example.foodiary.domain.model.SmartCoachScoreSection
import com.example.foodiary.domain.model.SmartCoachWeatherContext
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.model.WeatherFoodRecommendation
import com.example.foodiary.domain.model.WeatherNutritionFocus
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.repository.MealRepository
import com.example.foodiary.domain.repository.UserRepository
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

class GetSmartCoachInsightUseCase(
    private val foodRepository: FoodRepository,
    private val mealRepository: MealRepository,
    private val userRepository: UserRepository,
    private val nutritionTargetsResolver: (User) -> NutritionTargets
) {

    suspend operator fun invoke(
        dayStart: Long,
        dailyNutrition: DailyNutrition,
        recommendations: List<FoodRecommendation>,
        weatherRecommendation: WeatherFoodRecommendation?,
        nowMillis: Long = System.currentTimeMillis()
    ): SmartCoachInsight? {
        val user = userRepository.getCurrentUser() ?: return null
        val targets = nutritionTargetsResolver(user)
        val dayMeals = runCatching {
            mealRepository.getMealsForPeriod(dayStart, endOfDay(dayStart))
        }.getOrDefault(emptyList())
        val consumedFoodIds = dayMeals.map { it.foodId }.toSet()
        val availableRecommendations = recommendations.filterNot { it.food.id in consumedFoodIds }
        val forecast = buildForecast(dailyNutrition, targets, nowMillis)
        val balanceScore = calculateBalanceScore(forecast, targets)
        val focus = resolveFocus(dailyNutrition, forecast, targets, user.goal)
        val suggestedRecommendation = selectSuggestion(availableRecommendations, focus)
        val suggestedFood = suggestedRecommendation?.food
        val suggestedMealType = resolveSuggestedMealType(nowMillis)
        val weatherContext = weatherRecommendation
            ?.takeIf { it.focus != null || it.food != null }
            ?.let(::buildWeatherContext)
        val mealPlan = buildMealPlan(
            dailyNutrition = dailyNutrition,
            targets = targets,
            recommendations = availableRecommendations,
            focus = focus,
            weatherContext = weatherContext,
            dayMeals = dayMeals,
            nowMillis = nowMillis
        )

        return SmartCoachInsight(
            balanceScore = balanceScore,
            balanceTitle = buildBalanceTitle(
                dailyNutrition = dailyNutrition,
                forecast = forecast,
                targets = targets,
                focus = focus,
                score = balanceScore
            ),
            balanceMessage = buildBalanceMessage(forecast, targets, focus),
            focus = focus,
            forecast = forecast,
            correctionTitle = buildCorrectionTitle(focus),
            correctionMessage = buildCorrectionMessage(focus, suggestedRecommendation, weatherContext),
            suggestedFood = suggestedFood,
            suggestedMealType = suggestedMealType,
            replacement = null,
            scoreDetails = buildScoreDetails(
                dailyNutrition = dailyNutrition,
                targets = targets,
                recommendation = suggestedRecommendation,
                replacement = null,
                weatherContext = weatherContext,
                balanceScore = balanceScore
            ),
            explanationBullets = buildExplanationBullets(suggestedRecommendation, focus, weatherContext),
            weatherContext = weatherContext,
            mealPlan = mealPlan
        )
    }

    private fun buildForecast(
        nutrition: DailyNutrition,
        targets: NutritionTargets,
        nowMillis: Long
    ): SmartCoachForecast {
        val progress = estimateEatingDayProgress(nowMillis)
        val projectedCalories = project(nutrition.totalCalories, progress)
        val projectedProtein = project(nutrition.totalProtein, progress)
        val projectedFat = project(nutrition.totalFat, progress)
        val projectedCarbs = project(nutrition.totalCarbs, progress)
        val calorieDiff = projectedCalories - targets.targetCalories
        val proteinDiff = projectedProtein - targets.proteinGrams
        val fatDiff = projectedFat - targets.fatGrams
        val carbsDiff = projectedCarbs - targets.carbsGrams
        val isEarlyDay = progress < 0.36

        val title = when {
            nutrition.mealsCount == 0 -> "Прогноз появится после первого приема пищи"
            isEarlyDay && projectedCalories < targets.targetCalories * 0.55 ->
                "День только набирает темп"
            projectedCalories < targets.targetCalories * 0.55 && projectedProtein < targets.proteinGrams * 0.60 ->
                "Темп питания сильно ниже плана"
            abs(calorieDiff) <= targets.targetCalories * 0.08 && proteinDiff >= -12 ->
                "День идет близко к плану"
            calorieDiff > targets.targetCalories * 0.10 ->
                "Есть риск превысить калорийность"
            fatDiff > targets.fatGrams * 0.12 ->
                "Жиры могут выйти выше нормы"
            proteinDiff < -15 ->
                "К концу дня может не хватить белка"
            carbsDiff < -targets.carbsGrams * 0.22 ->
                "Может не хватить углеводов"
            else -> "Нужна мягкая корректировка рациона"
        }

        val message = if (nutrition.mealsCount == 0) {
            "Умный помощник ждет первые записи дневника, чтобы оценить темп дня и предложить точную коррекцию."
        } else if (isEarlyDay) {
            "Пока данных за день немного, поэтому прогноз ориентировочный: $projectedCalories ккал, " +
                "$projectedProtein г белка, $projectedFat г жиров и $projectedCarbs г углеводов. Помощник будет мягко уточнять план после следующих приемов пищи."
        } else {
            "По текущему темпу и времени дня прогноз: $projectedCalories ккал, " +
                "$projectedProtein г белка, $projectedFat г жиров и $projectedCarbs г углеводов."
        }

        return SmartCoachForecast(
            projectedCalories = projectedCalories,
            projectedProtein = projectedProtein,
            projectedFat = projectedFat,
            projectedCarbs = projectedCarbs,
            title = title,
            message = message
        )
    }

    private fun calculateBalanceScore(
        forecast: SmartCoachForecast,
        targets: NutritionTargets
    ): Int {
        val calorieScore = adherenceScore(forecast.projectedCalories.toDouble(), targets.targetCalories.toDouble())
        val proteinScore = lowerBoundScore(forecast.projectedProtein.toDouble(), targets.proteinGrams.toDouble())
        val fatScore = adherenceScore(forecast.projectedFat.toDouble(), targets.fatGrams.toDouble())
        val carbsScore = adherenceScore(forecast.projectedCarbs.toDouble(), targets.carbsGrams.toDouble())

        return (
            calorieScore * 0.35 +
                proteinScore * 0.30 +
                fatScore * 0.15 +
                carbsScore * 0.20
            ).roundToInt().coerceIn(0, 100)
    }

    private fun resolveFocus(
        nutrition: DailyNutrition,
        forecast: SmartCoachForecast,
        targets: NutritionTargets,
        goal: UserGoal
    ): SmartCoachFocus {
        val calorieRemaining = targets.targetCalories - nutrition.totalCalories
        val proteinRemaining = targets.proteinGrams - nutrition.totalProtein
        val fatRemaining = targets.fatGrams - nutrition.totalFat
        val carbsRemaining = targets.carbsGrams - nutrition.totalCarbs
        val projectedCalorieExcess = forecast.projectedCalories - targets.targetCalories

        if (projectedCalorieExcess > targets.targetCalories * 0.10 && goal == UserGoal.WEIGHT_LOSS) {
            return SmartCoachFocus.CALORIES_EXCESS
        }
        if (proteinRemaining > targets.proteinGrams * 0.18) return SmartCoachFocus.PROTEIN_DEFICIT
        if (calorieRemaining > targets.targetCalories * 0.30) return SmartCoachFocus.CALORIES_DEFICIT
        if (carbsRemaining > targets.carbsGrams * 0.24) return SmartCoachFocus.CARBS_DEFICIT
        if (fatRemaining > targets.fatGrams * 0.24) return SmartCoachFocus.FAT_DEFICIT
        if (projectedCalorieExcess > targets.targetCalories * 0.12) return SmartCoachFocus.CALORIES_EXCESS
        return SmartCoachFocus.BALANCED
    }

    private fun selectSuggestion(
        recommendations: List<FoodRecommendation>,
        focus: SmartCoachFocus
    ): FoodRecommendation? {
        if (recommendations.isEmpty()) return null
        val selector: (FoodRecommendation) -> Int = when (focus) {
            SmartCoachFocus.PROTEIN_DEFICIT -> {
                {
                    it.breakdown.macroGapScore +
                        it.breakdown.portionPracticalityScore / 2 +
                        it.breakdown.mealTimingScore / 2 +
                        it.food.proteinPer100g.roundToInt()
                }
            }
            SmartCoachFocus.CALORIES_EXCESS -> {
                {
                    it.breakdown.goalFitScore +
                        inverseCaloriesScore(it.food) +
                        it.breakdown.portionPracticalityScore / 2 +
                        it.breakdown.mealTimingScore / 3
                }
            }
            SmartCoachFocus.CARBS_DEFICIT,
            SmartCoachFocus.FAT_DEFICIT,
            SmartCoachFocus.CALORIES_DEFICIT -> {
                {
                    it.breakdown.macroGapScore +
                        it.breakdown.goalFitScore +
                        it.breakdown.portionPracticalityScore / 2 +
                        it.breakdown.mealTimingScore / 3
                }
            }
            SmartCoachFocus.BALANCED -> {
                {
                    it.totalScore +
                        it.breakdown.varietyScore +
                        it.breakdown.roleBalanceScore / 2 +
                        it.breakdown.confidenceScore / 3
                }
            }
        }
        return recommendations.maxWithOrNull(
            compareBy<FoodRecommendation> { selector(it) }
                .thenBy { it.totalScore }
            )
    }

    private fun buildMealPlan(
        dailyNutrition: DailyNutrition,
        targets: NutritionTargets,
        recommendations: List<FoodRecommendation>,
        focus: SmartCoachFocus,
        weatherContext: SmartCoachWeatherContext?,
        dayMeals: List<Meal>,
        nowMillis: Long
    ): SmartCoachMealPlan? {
        if (recommendations.isEmpty()) return null

        val mealTypes = resolveMealPlanMealTypes(dailyNutrition, nowMillis)
        if (mealTypes.isEmpty()) return null
        val reservedFoodIds = dayMeals.map { it.foodId }.toMutableSet()

        val remaining = RemainingNutrition(
            calories = (targets.targetCalories - dailyNutrition.totalCalories)
                .coerceAtLeast((targets.targetCalories * 0.10).coerceAtLeast(160.0)),
            protein = (targets.proteinGrams - dailyNutrition.totalProtein).coerceAtLeast(0.0),
            fat = (targets.fatGrams - dailyNutrition.totalFat).coerceAtLeast(0.0),
            carbs = (targets.carbsGrams - dailyNutrition.totalCarbs).coerceAtLeast(0.0)
        )
        val totalShare = mealTypes.sumOf(::mealPlanShare).coerceAtLeast(1.0)

        val sections = mealTypes.mapNotNull { mealType ->
            val share = mealPlanShare(mealType) / totalShare
            val target = MealPlanTarget(
                calories = (remaining.calories * share)
                    .roundToInt()
                    .coerceAtLeast(minMealPlanCalories(mealType)),
                protein = (remaining.protein * share).roundToInt(),
                fat = (remaining.fat * share).roundToInt(),
                carbs = (remaining.carbs * share).roundToInt()
            )
            val options = buildMealPlanOptions(
                mealType = mealType,
                target = target,
                recommendations = recommendations,
                focus = focus,
                weatherContext = weatherContext,
                excludedFoodIds = reservedFoodIds.toSet()
            )

            if (options.isEmpty()) {
                null
            } else {
                reservedFoodIds += options.flatMap { option -> option.items.map { it.food.id } }
                SmartCoachMealPlanSection(
                    mealType = mealType,
                    title = mealTypeLabelForPlan(mealType),
                    subtitle = buildMealPlanSectionSubtitle(mealType, target),
                    targetCalories = target.calories,
                    targetProtein = target.protein,
                    targetFat = target.fat,
                    targetCarbs = target.carbs,
                    options = options
                )
            }
        }

        if (sections.isEmpty()) return null

        return SmartCoachMealPlan(
            title = "План до конца дня",
            subtitle = "Это сценарий питания по приемам пищи. Он отличается от быстрой рекомендации: здесь Foodiary раскладывает остаток КБЖУ на несколько вариантов до конца дня.",
            sections = sections
        )
    }

    private fun buildMealPlanOptions(
        mealType: MealType,
        target: MealPlanTarget,
        recommendations: List<FoodRecommendation>,
        focus: SmartCoachFocus,
        weatherContext: SmartCoachWeatherContext?,
        excludedFoodIds: Set<String>
    ): List<SmartCoachMealPlanOption> {
        val freshRecommendations = recommendations.filterNot { it.food.id in excludedFoodIds }
        if (freshRecommendations.isEmpty()) return emptyList()

        val ranked = freshRecommendations
            .sortedWith(
                compareByDescending<FoodRecommendation> { mealPlanCandidateScore(it, mealType, focus) }
                    .thenByDescending { it.totalScore }
            )
        val suitable = ranked.filter { isSuitableForMealPlan(it.food, mealType) }
        if (suitable.isEmpty()) return emptyList()
        val options = mutableListOf<SmartCoachMealPlanOption>()

        suitable.firstOrNull { !isRecipeFood(it.food) }?.let { recommendation ->
            options += buildSingleMealPlanOption(
                idPrefix = "single",
                title = buildSingleOptionTitle(focus),
                mealType = mealType,
                target = target,
                recommendation = recommendation,
                focus = focus,
                weatherContext = weatherContext
            )
        }

        buildCombinationMealPlanOption(
            mealType = mealType,
            target = target,
            recommendations = suitable,
            focus = focus,
            weatherContext = weatherContext
        )?.let(options::add)

        suitable.firstOrNull { isRecipeFood(it.food) }?.let { recommendation ->
            options += buildSingleMealPlanOption(
                idPrefix = "recipe",
                title = "Пользовательский рецепт",
                mealType = mealType,
                target = target,
                recommendation = recommendation,
                focus = focus,
                weatherContext = weatherContext
            )
        }

        if (options.size < 2) {
            suitable
                .filter { candidate -> options.none { option -> option.items.any { it.food.id == candidate.food.id } } }
                .take(2 - options.size)
                .forEach { recommendation ->
                    options += buildSingleMealPlanOption(
                        idPrefix = "extra",
                        title = "Альтернативный вариант",
                        mealType = mealType,
                        target = target,
                        recommendation = recommendation,
                        focus = focus,
                        weatherContext = weatherContext
                    )
                }
        }

        return options
            .distinctBy { option -> option.items.joinToString("+") { it.food.id } }
            .sortedByDescending { it.score }
            .take(3)
    }

    private fun buildSingleMealPlanOption(
        idPrefix: String,
        title: String,
        mealType: MealType,
        target: MealPlanTarget,
        recommendation: FoodRecommendation,
        focus: SmartCoachFocus,
        weatherContext: SmartCoachWeatherContext?
    ): SmartCoachMealPlanOption {
        val food = recommendation.food
        val quantity = estimateSinglePortion(food, mealType, target, focus)
        val item = SmartCoachMealPlanItem(food = food, quantityInGrams = quantity)
        val nutrition = calculateMealPlanNutrition(listOf(item))
        val score = calculateMealPlanOptionScore(
            nutrition = nutrition,
            target = target,
            recommendationScore = recommendation.totalScore,
            mealType = mealType,
            foods = listOf(food)
        )

        return SmartCoachMealPlanOption(
            id = "$idPrefix-${mealType.name}-${food.id}",
            title = title,
            subtitle = if (isRecipeFood(food)) {
                "${food.name}, ${quantity} г"
            } else {
                "${food.name}, ${quantity} г"
            },
            items = listOf(item),
            calories = nutrition.calories,
            protein = nutrition.protein,
            fat = nutrition.fat,
            carbs = nutrition.carbs,
            score = score,
            reason = buildMealPlanOptionReason(
                mealType = mealType,
                target = target,
                nutrition = nutrition,
                recommendations = listOf(recommendation),
                focus = focus,
                weatherContext = weatherContext,
                isCombination = false
            )
        )
    }

    private fun buildCombinationMealPlanOption(
        mealType: MealType,
        target: MealPlanTarget,
        recommendations: List<FoodRecommendation>,
        focus: SmartCoachFocus,
        weatherContext: SmartCoachWeatherContext?
    ): SmartCoachMealPlanOption? {
        val pair = selectCompatibleMealPlanPair(
            mealType = mealType,
            recommendations = recommendations
        ) ?: return null
        val (protein, support) = orderMealPlanPair(pair)
        val proteinFood = protein.food
        val supportFood = support.food
        val proteinTarget = when (focus) {
            SmartCoachFocus.PROTEIN_DEFICIT -> target.protein * 0.72
            else -> target.protein * 0.55
        }
        val proteinQuantity = estimatePortionByProteinOrCalories(
            food = proteinFood,
            mealType = mealType,
            proteinTarget = proteinTarget,
            calorieTarget = target.calories * 0.52
        )
        val proteinCalories = proteinFood.caloriesPer100g * proteinQuantity / 100.0
        val supportQuantity = estimatePortionByProteinOrCalories(
            food = supportFood,
            mealType = mealType,
            proteinTarget = target.protein * 0.20,
            calorieTarget = (target.calories - proteinCalories).coerceAtLeast(target.calories * 0.25)
        )
        val items = listOf(
            SmartCoachMealPlanItem(proteinFood, proteinQuantity),
            SmartCoachMealPlanItem(supportFood, supportQuantity)
        )
        val nutrition = calculateMealPlanNutrition(items)
        val score = calculateMealPlanOptionScore(
            nutrition = nutrition,
            target = target,
            recommendationScore = (protein.totalScore + support.totalScore) / 2,
            mealType = mealType,
            foods = listOf(proteinFood, supportFood)
        )

        return SmartCoachMealPlanOption(
            id = "combo-${mealType.name}-${proteinFood.id}-${supportFood.id}",
            title = "Комбинация продуктов",
            subtitle = "${proteinFood.name} + ${supportFood.name}",
            items = items,
            calories = nutrition.calories,
            protein = nutrition.protein,
            fat = nutrition.fat,
            carbs = nutrition.carbs,
            score = score,
            reason = buildMealPlanOptionReason(
                mealType = mealType,
                target = target,
                nutrition = nutrition,
                recommendations = listOf(protein, support),
                focus = focus,
                weatherContext = weatherContext,
                isCombination = true
            )
        )
    }

    private fun selectCompatibleMealPlanPair(
        mealType: MealType,
        recommendations: List<FoodRecommendation>
    ): MealPlanPairCandidate? {
        val candidates = recommendations.take(14)
        return candidates
            .flatMapIndexed { index, first ->
                candidates.drop(index + 1).map { second ->
                    MealPlanPairCandidate(
                        first = first,
                        second = second,
                        compatibilityScore = mealPairCompatibilityScore(first.food, second.food, mealType)
                    )
                }
            }
            .filter { it.compatibilityScore >= 58 }
            .maxWithOrNull(
                compareBy<MealPlanPairCandidate> {
                    it.compatibilityScore +
                        ((it.first.totalScore + it.second.totalScore) / 8)
                }.thenBy {
                    mealPlanPrimaryPriority(foodRole(it.first.food)) +
                        mealPlanPrimaryPriority(foodRole(it.second.food))
                }
            )
    }

    private fun orderMealPlanPair(
        pair: MealPlanPairCandidate
    ): Pair<FoodRecommendation, FoodRecommendation> {
        val firstPriority = mealPlanPrimaryPriority(foodRole(pair.first.food))
        val secondPriority = mealPlanPrimaryPriority(foodRole(pair.second.food))
        return if (firstPriority >= secondPriority) {
            pair.first to pair.second
        } else {
            pair.second to pair.first
        }
    }

    private fun mealPlanPrimaryPriority(role: FoodRole): Int {
        return when (role) {
            FoodRole.PROTEIN_MAIN -> 6
            FoodRole.CUSTOM_RECIPE -> 5
            FoodRole.DAIRY_SNACK -> 4
            FoodRole.GRAIN_SIDE -> 3
            FoodRole.LIGHT_PRODUCE -> 2
            FoodRole.FRUIT_SNACK,
            FoodRole.FAT_SNACK -> 1
            else -> 0
        }
    }

    private fun mealPairCompatibilityScore(
        first: Food,
        second: Food,
        mealType: MealType
    ): Int {
        if (first.id == second.id) return -100

        val firstRole = foodRole(first)
        val secondRole = foodRole(second)
        val roles = setOf(firstRole, secondRole)
        if (roles.any { it in setOf(FoodRole.BEVERAGE, FoodRole.SAUCE_OR_EXTRA, FoodRole.SWEET_SNACK) }) {
            return -70
        }
        if (hasPair(first, second, ::isSavoryProtein, ::isBreakfastGrain)) {
            return -80
        }

        return when (mealType) {
            MealType.BREAKFAST -> breakfastPairScore(first, second, roles)
            MealType.LUNCH,
            MealType.DINNER -> mainMealPairScore(first, second, roles)
            MealType.SNACK,
            MealType.AFTERNOON_SNACK -> snackPairScore(first, second, roles)
            MealType.LATE_DINNER -> lateDinnerPairScore(first, second, roles)
        }
    }

    private fun breakfastPairScore(
        first: Food,
        second: Food,
        roles: Set<FoodRole>
    ): Int {
        if (isSavoryProtein(first) || isSavoryProtein(second)) return 24
        return when {
            hasPair(first, second, ::isBreakfastProtein, ::isBreakfastGrain) -> 94
            hasPair(first, second, ::isBreakfastProtein) { food -> foodRole(food) == FoodRole.FRUIT_SNACK } -> 88
            hasRolePair(roles, FoodRole.GRAIN_SIDE, FoodRole.FRUIT_SNACK) -> 84
            hasRolePair(roles, FoodRole.DAIRY_SNACK, FoodRole.FAT_SNACK) -> 78
            FoodRole.CUSTOM_RECIPE in roles -> 72
            else -> 52
        }
    }

    private fun mainMealPairScore(
        first: Food,
        second: Food,
        roles: Set<FoodRole>
    ): Int {
        if (isBreakfastGrain(first) || isBreakfastGrain(second)) return 20
        return when {
            hasRolePair(roles, FoodRole.PROTEIN_MAIN, FoodRole.GRAIN_SIDE) -> 96
            hasRolePair(roles, FoodRole.PROTEIN_MAIN, FoodRole.LIGHT_PRODUCE) -> 90
            hasRolePair(roles, FoodRole.GRAIN_SIDE, FoodRole.LIGHT_PRODUCE) -> 78
            FoodRole.CUSTOM_RECIPE in roles && roles.any {
                it in setOf(FoodRole.PROTEIN_MAIN, FoodRole.GRAIN_SIDE, FoodRole.LIGHT_PRODUCE)
            } -> 82
            else -> 54
        }
    }

    private fun snackPairScore(
        first: Food,
        second: Food,
        roles: Set<FoodRole>
    ): Int {
        if (isSavoryProtein(first) || isSavoryProtein(second)) return 22
        return when {
            hasRolePair(roles, FoodRole.DAIRY_SNACK, FoodRole.FRUIT_SNACK) -> 94
            hasRolePair(roles, FoodRole.FRUIT_SNACK, FoodRole.FAT_SNACK) -> 88
            hasPair(first, second, ::isBreakfastProtein, ::isBreakfastGrain) -> 84
            hasRolePair(roles, FoodRole.DAIRY_SNACK, FoodRole.FAT_SNACK) -> 82
            FoodRole.CUSTOM_RECIPE in roles -> 66
            else -> 56
        }
    }

    private fun lateDinnerPairScore(
        first: Food,
        second: Food,
        roles: Set<FoodRole>
    ): Int {
        if (isBreakfastGrain(first) || isBreakfastGrain(second) || FoodRole.FAT_SNACK in roles) return 18
        return when {
            hasRolePair(roles, FoodRole.PROTEIN_MAIN, FoodRole.LIGHT_PRODUCE) -> 88
            hasRolePair(roles, FoodRole.DAIRY_SNACK, FoodRole.LIGHT_PRODUCE) -> 84
            hasRolePair(roles, FoodRole.PROTEIN_MAIN, FoodRole.DAIRY_SNACK) -> 70
            FoodRole.CUSTOM_RECIPE in roles && FoodRole.LIGHT_PRODUCE in roles -> 72
            else -> 48
        }
    }

    private fun hasRolePair(
        roles: Set<FoodRole>,
        first: FoodRole,
        second: FoodRole
    ): Boolean {
        return first in roles && second in roles
    }

    private fun hasPair(
        first: Food,
        second: Food,
        firstPredicate: (Food) -> Boolean,
        secondPredicate: (Food) -> Boolean
    ): Boolean {
        return (firstPredicate(first) && secondPredicate(second)) ||
            (firstPredicate(second) && secondPredicate(first))
    }

    private fun resolveMealPlanMealTypes(
        dailyNutrition: DailyNutrition,
        nowMillis: Long
    ): List<MealType> {
        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }
            .get(Calendar.HOUR_OF_DAY)
        val candidates = when (hour) {
            in 0..10 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
            in 11..13 -> listOf(MealType.LUNCH, MealType.SNACK, MealType.DINNER)
            in 14..16 -> listOf(MealType.SNACK, MealType.DINNER, MealType.LATE_DINNER)
            in 17..20 -> listOf(MealType.DINNER, MealType.LATE_DINNER)
            else -> listOf(MealType.LATE_DINNER)
        }
        val emptyOrCurrent = candidates.filter { mealType ->
            (dailyNutrition.mealsByType[mealType] ?: 0) == 0 || mealType == resolveSuggestedMealType(nowMillis)
        }
        return emptyOrCurrent.ifEmpty { listOf(resolveSuggestedMealType(nowMillis)) }.take(3)
    }

    private fun mealPlanCandidateScore(
        recommendation: FoodRecommendation,
        mealType: MealType,
        focus: SmartCoachFocus
    ): Int {
        val food = recommendation.food
        val roleScore = mealPlanRoleScore(food, mealType)
        val focusBonus = when (focus) {
            SmartCoachFocus.PROTEIN_DEFICIT -> (food.proteinPer100g * 1.4).roundToInt()
            SmartCoachFocus.CALORIES_EXCESS -> inverseCaloriesScore(food) / 2
            SmartCoachFocus.CARBS_DEFICIT -> (food.carbsPer100g * 0.9).roundToInt()
            SmartCoachFocus.FAT_DEFICIT -> (food.fatPer100g * 1.5).roundToInt()
            SmartCoachFocus.CALORIES_DEFICIT -> normalizeCaloriesBonus(food)
            SmartCoachFocus.BALANCED -> recommendation.breakdown.varietyScore / 3
        }
        val recipeBonus = if (isRecipeFood(food)) 14 else 0
        return recommendation.totalScore + roleScore + focusBonus + recipeBonus
    }

    private fun mealPlanRoleScore(food: Food, mealType: MealType): Int {
        return when (mealType) {
            MealType.BREAKFAST -> when (foodRole(food)) {
                FoodRole.GRAIN_SIDE,
                FoodRole.FRUIT_SNACK,
                FoodRole.DAIRY_SNACK,
                FoodRole.PROTEIN_MAIN,
                FoodRole.CUSTOM_RECIPE -> 34
                FoodRole.FAT_SNACK -> 20
                FoodRole.LIGHT_PRODUCE -> 14
                else -> 4
            }
            MealType.LUNCH,
            MealType.DINNER -> when (foodRole(food)) {
                FoodRole.PROTEIN_MAIN,
                FoodRole.GRAIN_SIDE,
                FoodRole.LIGHT_PRODUCE,
                FoodRole.CUSTOM_RECIPE -> 36
                FoodRole.DAIRY_SNACK,
                FoodRole.FRUIT_SNACK -> 14
                else -> 6
            }
            MealType.SNACK,
            MealType.AFTERNOON_SNACK -> when (foodRole(food)) {
                FoodRole.FRUIT_SNACK,
                FoodRole.DAIRY_SNACK,
                FoodRole.FAT_SNACK,
                FoodRole.LIGHT_PRODUCE -> 36
                FoodRole.PROTEIN_MAIN,
                FoodRole.CUSTOM_RECIPE -> 18
                else -> 8
            }
            MealType.LATE_DINNER -> when (foodRole(food)) {
                FoodRole.DAIRY_SNACK,
                FoodRole.PROTEIN_MAIN,
                FoodRole.LIGHT_PRODUCE -> 36
                FoodRole.FRUIT_SNACK,
                FoodRole.CUSTOM_RECIPE -> 16
                else -> 4
            }
        }
    }

    private fun isSuitableForMealPlan(food: Food, mealType: MealType): Boolean {
        if (isRecipeFood(food)) return true
        val role = foodRole(food)
        return when (mealType) {
            MealType.BREAKFAST ->
                role !in setOf(FoodRole.SAUCE_OR_EXTRA, FoodRole.BEVERAGE, FoodRole.SWEET_SNACK) &&
                    !isSavoryProtein(food)
            MealType.LUNCH,
            MealType.DINNER ->
                role !in setOf(FoodRole.SWEET_SNACK, FoodRole.BEVERAGE, FoodRole.SAUCE_OR_EXTRA) &&
                    !isBreakfastGrain(food)
            MealType.SNACK,
            MealType.AFTERNOON_SNACK ->
                role !in setOf(FoodRole.SAUCE_OR_EXTRA, FoodRole.BEVERAGE) &&
                    !isSavoryProtein(food)
            MealType.LATE_DINNER ->
                role !in setOf(FoodRole.SWEET_SNACK, FoodRole.FAT_SNACK, FoodRole.BEVERAGE, FoodRole.SAUCE_OR_EXTRA) &&
                    !isBreakfastGrain(food) &&
                    food.caloriesPer100g <= 260.0
        }
    }

    private fun estimateSinglePortion(
        food: Food,
        mealType: MealType,
        target: MealPlanTarget,
        focus: SmartCoachFocus
    ): Int {
        val proteinTarget = if (focus == SmartCoachFocus.PROTEIN_DEFICIT) {
            target.protein * 0.82
        } else {
            target.protein * 0.55
        }
        val calorieShare = when {
            mealType == MealType.LATE_DINNER && target.calories <= 260 -> 0.92
            mealType == MealType.LATE_DINNER -> 0.78
            else -> 0.72
        }
        return estimatePortionByProteinOrCalories(
            food = food,
            mealType = mealType,
            proteinTarget = proteinTarget,
            calorieTarget = target.calories * calorieShare
        )
    }

    private fun estimatePortionByProteinOrCalories(
        food: Food,
        mealType: MealType,
        proteinTarget: Double,
        calorieTarget: Double
    ): Int {
        val byProtein = if (proteinTarget > 0 && food.proteinPer100g > 0.0) {
            proteinTarget / food.proteinPer100g * 100.0
        } else {
            null
        }
        val byCalories = if (calorieTarget > 0 && food.caloriesPer100g > 0.0) {
            calorieTarget / food.caloriesPer100g * 100.0
        } else {
            null
        }
        val raw = listOfNotNull(byProtein, byCalories).average().takeIf { !it.isNaN() }
            ?: practicalRange(foodRole(food), mealType).defaultGrams
        val range = practicalRange(foodRole(food), mealType)
        return raw
            .coerceIn(range.minGrams, range.maxGrams)
            .roundToInt()
            .coerceAtLeast(10)
    }

    private fun calculateMealPlanNutrition(
        items: List<SmartCoachMealPlanItem>
    ): PlanNutrition {
        var calories = 0.0
        var protein = 0.0
        var fat = 0.0
        var carbs = 0.0
        items.forEach { item ->
            val multiplier = item.quantityInGrams / 100.0
            calories += item.food.caloriesPer100g * multiplier
            protein += item.food.proteinPer100g * multiplier
            fat += item.food.fatPer100g * multiplier
            carbs += item.food.carbsPer100g * multiplier
        }
        return PlanNutrition(
            calories = calories.roundToInt(),
            protein = protein.roundToInt(),
            fat = fat.roundToInt(),
            carbs = carbs.roundToInt()
        )
    }

    private fun calculateMealPlanOptionScore(
        nutrition: PlanNutrition,
        target: MealPlanTarget,
        recommendationScore: Int,
        mealType: MealType,
        foods: List<Food>
    ): Int {
        val calorieScore = adherenceScore(nutrition.calories.toDouble(), target.calories.toDouble())
        val proteinScore = if (target.protein <= 0) {
            76
        } else {
            lowerBoundScore(nutrition.protein.toDouble(), target.protein.toDouble())
        }
        val fatScore = if (target.fat <= 0) {
            78
        } else {
            adherenceScore(nutrition.fat.toDouble(), target.fat.toDouble())
        }
        val carbsScore = if (target.carbs <= 0) {
            78
        } else {
            adherenceScore(nutrition.carbs.toDouble(), target.carbs.toDouble())
        }
        val roleScore = foods.map { mealPlanRoleScore(it, mealType) }.average().takeIf { !it.isNaN() } ?: 20.0
        return (
            calorieScore * 0.26 +
                proteinScore * 0.24 +
                fatScore * 0.10 +
                carbsScore * 0.14 +
                recommendationScore * 0.16 +
                roleScore * 0.10
            ).roundToInt().coerceIn(0, 100)
    }

    private fun buildMealPlanOptionReason(
        mealType: MealType,
        target: MealPlanTarget,
        nutrition: PlanNutrition,
        recommendations: List<FoodRecommendation>,
        focus: SmartCoachFocus,
        weatherContext: SmartCoachWeatherContext?,
        isCombination: Boolean
    ): String {
        val names = recommendations.joinToString(", ") { it.food.name }
        val optionType = if (isCombination) {
            "Комбинация выбрана, чтобы один продукт закрывал основной дефицит, а второй выравнивал калории и соседние макронутриенты."
        } else {
            "Вариант выбран как самостоятельный прием пищи или основа приема пищи."
        }
        val focusText = when (focus) {
            SmartCoachFocus.PROTEIN_DEFICIT -> "Главный приоритет сейчас - добрать белок без случайного перебора калорий."
            SmartCoachFocus.CALORIES_EXCESS -> "Главный приоритет сейчас - не перегрузить остаток дня калориями."
            SmartCoachFocus.CALORIES_DEFICIT -> "Главный приоритет сейчас - закрыть недостающую энергию."
            SmartCoachFocus.CARBS_DEFICIT -> "Главный приоритет сейчас - добавить углеводную энергию."
            SmartCoachFocus.FAT_DEFICIT -> "Главный приоритет сейчас - мягко добрать жиры."
            SmartCoachFocus.BALANCED -> "Главный приоритет сейчас - сохранить текущий баланс."
        }
        val targetText = "Для ${mealTypeLabelForPlan(mealType).lowercase()} ориентир: около ${target.calories} ккал, " +
            "${target.protein} г белка, ${target.fat} г жиров и ${target.carbs} г углеводов."
        val resultText = "Этот вариант дает примерно ${nutrition.calories} ккал, ${nutrition.protein} г белка, " +
            "${nutrition.fat} г жиров и ${nutrition.carbs} г углеводов."
        val lateDinnerText = if (mealType == MealType.LATE_DINNER) {
            "Поздний ужин рассчитан как легкий резерв после основного ужина: он помогает добрать часть остатка без тяжелой порции перед сном."
        } else {
            null
        }
        val compatibilityText = if (isCombination) {
            "Оба продукта прошли проверку совместимости, основанную на приеме пищи и их роли."
        } else {
            null
        }
        val recommendationText = recommendations
            .mapNotNull { it.secondaryReason ?: it.primaryReason }
            .distinct()
            .take(2)
            .joinToString(" ")
            .ifBlank { "Учтены текущий остаток КБЖУ, цель пользователя, уместность приема пищи и практичность порции." }
        val weatherText = weatherContext?.let {
            "Также учтен погодный контекст: ${it.message}"
        }
        return listOfNotNull(
            "$names включены в план не случайно.",
            optionType,
            focusText,
            targetText,
            resultText,
            lateDinnerText,
            compatibilityText,
            recommendationText,
            weatherText
        ).joinToString(" ")
    }

    private fun buildSingleOptionTitle(focus: SmartCoachFocus): String {
        return when (focus) {
            SmartCoachFocus.PROTEIN_DEFICIT -> "Добрать белок"
            SmartCoachFocus.CALORIES_EXCESS -> "Легкий вариант"
            SmartCoachFocus.CALORIES_DEFICIT -> "Питательный вариант"
            SmartCoachFocus.CARBS_DEFICIT -> "Добавить энергию"
            SmartCoachFocus.FAT_DEFICIT -> "Добрать жиры"
            SmartCoachFocus.BALANCED -> "Сбалансированный вариант"
        }
    }

    private fun buildMealPlanSectionSubtitle(
        mealType: MealType,
        target: MealPlanTarget
    ): String {
        if (mealType == MealType.LATE_DINNER) {
            return "Легкий резерв: ${target.calories} ккал, ${target.protein} Б / ${target.fat} Ж / ${target.carbs} У"
        }
        return "Ориентир: ${target.calories} ккал, ${target.protein} Б / ${target.fat} Ж / ${target.carbs} У"
    }

    private fun mealTypeLabelForPlan(type: MealType): String {
        return when (type) {
            MealType.BREAKFAST -> "Завтрак"
            MealType.LUNCH -> "Обед"
            MealType.DINNER -> "Ужин"
            MealType.SNACK -> "Перекус"
            MealType.AFTERNOON_SNACK -> "Полдник"
            MealType.LATE_DINNER -> "Поздний ужин"
        }
    }

    private fun mealPlanShare(type: MealType): Double {
        return when (type) {
            MealType.BREAKFAST -> 0.24
            MealType.LUNCH -> 0.34
            MealType.DINNER -> 0.28
            MealType.SNACK,
            MealType.AFTERNOON_SNACK -> 0.14
            MealType.LATE_DINNER -> 0.10
        }
    }

    private fun minMealPlanCalories(type: MealType): Int {
        return when (type) {
            MealType.BREAKFAST -> 220
            MealType.LUNCH -> 260
            MealType.DINNER -> 240
            MealType.SNACK,
            MealType.AFTERNOON_SNACK -> 120
            MealType.LATE_DINNER -> 100
        }
    }

    private fun practicalRange(
        role: FoodRole,
        mealType: MealType
    ): PlanPracticalRange {
        val base = when (role) {
            FoodRole.PROTEIN_MAIN -> PlanPracticalRange(80.0, 230.0, 140.0)
            FoodRole.GRAIN_SIDE -> PlanPracticalRange(70.0, 240.0, 130.0)
            FoodRole.LIGHT_PRODUCE -> PlanPracticalRange(120.0, 420.0, 220.0)
            FoodRole.FRUIT_SNACK -> PlanPracticalRange(90.0, 300.0, 150.0)
            FoodRole.DAIRY_SNACK -> PlanPracticalRange(100.0, 280.0, 180.0)
            FoodRole.FAT_SNACK -> PlanPracticalRange(15.0, 55.0, 28.0)
            FoodRole.SWEET_SNACK -> PlanPracticalRange(15.0, 70.0, 35.0)
            FoodRole.BEVERAGE -> PlanPracticalRange(150.0, 350.0, 250.0)
            FoodRole.SAUCE_OR_EXTRA -> PlanPracticalRange(5.0, 35.0, 15.0)
            FoodRole.CUSTOM_RECIPE -> PlanPracticalRange(160.0, 450.0, 280.0)
            FoodRole.OTHER -> PlanPracticalRange(60.0, 260.0, 140.0)
        }
        val multiplier = when (mealType) {
            MealType.SNACK,
            MealType.AFTERNOON_SNACK -> 0.72
            MealType.LATE_DINNER -> 0.62
            else -> 1.0
        }
        return PlanPracticalRange(
            minGrams = base.minGrams * multiplier,
            maxGrams = base.maxGrams * multiplier,
            defaultGrams = base.defaultGrams * multiplier
        )
    }

    private fun normalizeCaloriesBonus(food: Food): Int {
        return ((food.caloriesPer100g - 80.0) / 340.0 * 40)
            .roundToInt()
            .coerceIn(0, 40)
    }

    private fun isRecipeFood(food: Food): Boolean {
        return food.isCustom && food.category.equals("custom_recipe", ignoreCase = true)
    }

    private suspend fun buildReplacement(
        meals: List<Meal>,
        recommendations: List<FoodRecommendation>,
        user: User,
        focus: SmartCoachFocus
    ): SmartCoachReplacement? {
        if (meals.isEmpty() || recommendations.isEmpty()) return null

        val eatenFoods = meals.mapNotNull { meal ->
            runCatching { foodRepository.getFoodById(meal.foodId) }.getOrNull()
        }.distinctBy { it.id }
        if (eatenFoods.isEmpty()) return null

        val original = eatenFoods.maxByOrNull { food -> replacementNeedScore(food, user.goal, focus) }
            ?: return null
        val candidates = recommendations
            .asSequence()
            .map { it.food }
            .filter { it.id != original.id }
            .toList()
        val semanticCandidates = candidates.filter { semanticReplacementScore(original, it) >= 20 }
        val replacement = (semanticCandidates.ifEmpty { candidates })
            .maxByOrNull { food -> replacementBenefitScore(original, food, user.goal, focus) }
            ?: return null

        val calorieDelta = (replacement.caloriesPer100g - original.caloriesPer100g).roundToInt()
        val proteinDelta = (replacement.proteinPer100g - original.proteinPer100g).roundToInt()
        val semanticMatch = semanticReplacementLabel(original, replacement)
        val semanticScore = semanticReplacementScore(original, replacement)

        return SmartCoachReplacement(
            originalFood = original,
            replacement = replacement,
            title = "Умная замена",
            reason = buildReplacementReason(
                original = original,
                replacement = replacement,
                calorieDelta = calorieDelta,
                proteinDelta = proteinDelta,
                goal = user.goal,
                focus = focus,
                semanticMatch = semanticMatch
            ),
            calorieDeltaPer100g = calorieDelta,
            proteinDeltaPer100g = proteinDelta,
            semanticMatch = semanticMatch,
            semanticScore = semanticScore
        )
    }

    private fun replacementNeedScore(
        food: Food,
        goal: UserGoal,
        focus: SmartCoachFocus
    ): Double {
        return when {
            focus == SmartCoachFocus.CALORIES_EXCESS || goal == UserGoal.WEIGHT_LOSS ->
                food.caloriesPer100g * 0.55 + food.fatPer100g * 8.0 - food.proteinPer100g * 2.0
            focus == SmartCoachFocus.PROTEIN_DEFICIT || goal == UserGoal.MUSCLE_GAIN_TRAINING ->
                food.caloriesPer100g * 0.20 - food.proteinPer100g * 6.0 + food.fatPer100g * 3.0
            else ->
                abs(food.caloriesPer100g - 180.0) + abs(food.proteinPer100g - 12.0) * 4.0
        }
    }

    private fun replacementBenefitScore(
        original: Food,
        candidate: Food,
        goal: UserGoal,
        focus: SmartCoachFocus
    ): Double {
        val calorieDelta = original.caloriesPer100g - candidate.caloriesPer100g
        val proteinDelta = candidate.proteinPer100g - original.proteinPer100g
        val carbDelta = candidate.carbsPer100g - original.carbsPer100g
        val semanticScore = semanticReplacementScore(original, candidate).toDouble()
        val nutritionScore = when {
            focus == SmartCoachFocus.CALORIES_EXCESS || goal == UserGoal.WEIGHT_LOSS ->
                calorieDelta * 0.45 + proteinDelta * 6.0 - candidate.fatPer100g * 2.0
            focus == SmartCoachFocus.PROTEIN_DEFICIT || goal == UserGoal.MUSCLE_GAIN_TRAINING ->
                proteinDelta * 8.0 + carbDelta.coerceAtLeast(0.0) * 1.3 - abs(candidate.caloriesPer100g - 180.0) * 0.12
            else ->
                proteinDelta * 4.0 - abs(candidate.caloriesPer100g - 180.0) * 0.2
        }
        return nutritionScore + semanticScore * 1.35
    }

    private fun semanticReplacementScore(
        original: Food,
        candidate: Food
    ): Int {
        val originalRole = foodRole(original)
        val candidateRole = foodRole(candidate)
        val score = when {
            originalRole == candidateRole -> 86
            areCompatibleRoles(originalRole, candidateRole) -> 68
            originalRole != FoodRole.SWEET_SNACK &&
                candidateRole != FoodRole.SWEET_SNACK &&
                original.category.equals(candidate.category, ignoreCase = true) -> 58
            isSnackRole(originalRole) && isSnackRole(candidateRole) -> 54
            originalRole == FoodRole.OTHER || candidateRole == FoodRole.OTHER -> 36
            else -> 18
        }
        return score.coerceIn(0, 100)
    }

    private fun semanticReplacementLabel(
        original: Food,
        candidate: Food
    ): String {
        val originalRole = foodRole(original)
        val candidateRole = foodRole(candidate)
        return when {
            originalRole == candidateRole ->
                "замена сохраняет тот же тип продукта"
            isSnackRole(originalRole) && isSnackRole(candidateRole) ->
                "замена остается в формате перекуса"
            originalRole == FoodRole.PROTEIN_MAIN && candidateRole == FoodRole.PROTEIN_MAIN ->
                "замена остается белковым продуктом"
            originalRole == FoodRole.GRAIN_SIDE && candidateRole == FoodRole.GRAIN_SIDE ->
                "замена остается гарниром или углеводной основой"
            originalRole == FoodRole.SWEET_SNACK && candidateRole == FoodRole.DAIRY_SNACK ->
                "замена сохраняет формат сладкого перекуса, но дает больше пользы"
            originalRole == FoodRole.SWEET_SNACK && candidateRole == FoodRole.FRUIT_SNACK ->
                "замена остается сладким перекусом, но легче по составу"
            original.category.equals(candidate.category, ignoreCase = true) ->
                "замена остается в той же продуктовой категории"
            else ->
                "замена близка по роли в текущем рационе"
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

    private fun areCompatibleRoles(
        original: FoodRole,
        candidate: FoodRole
    ): Boolean {
        return when (original) {
            FoodRole.SWEET_SNACK ->
                candidate in setOf(FoodRole.SWEET_SNACK, FoodRole.FRUIT_SNACK, FoodRole.DAIRY_SNACK)
            FoodRole.FRUIT_SNACK ->
                candidate in setOf(FoodRole.FRUIT_SNACK, FoodRole.DAIRY_SNACK, FoodRole.SWEET_SNACK)
            FoodRole.DAIRY_SNACK ->
                candidate in setOf(FoodRole.DAIRY_SNACK, FoodRole.FRUIT_SNACK, FoodRole.PROTEIN_MAIN)
            FoodRole.FAT_SNACK ->
                candidate in setOf(FoodRole.FAT_SNACK, FoodRole.DAIRY_SNACK, FoodRole.PROTEIN_MAIN)
            FoodRole.GRAIN_SIDE ->
                candidate in setOf(FoodRole.GRAIN_SIDE, FoodRole.LIGHT_PRODUCE)
            FoodRole.PROTEIN_MAIN ->
                candidate in setOf(FoodRole.PROTEIN_MAIN, FoodRole.DAIRY_SNACK)
            FoodRole.LIGHT_PRODUCE ->
                candidate in setOf(FoodRole.LIGHT_PRODUCE, FoodRole.FRUIT_SNACK, FoodRole.GRAIN_SIDE)
            FoodRole.CUSTOM_RECIPE ->
                candidate == FoodRole.CUSTOM_RECIPE
            FoodRole.BEVERAGE ->
                candidate == FoodRole.BEVERAGE
            FoodRole.SAUCE_OR_EXTRA ->
                candidate == FoodRole.SAUCE_OR_EXTRA
            FoodRole.OTHER ->
                false
        }
    }

    private fun isSnackRole(role: FoodRole): Boolean {
        return role in setOf(
            FoodRole.SWEET_SNACK,
            FoodRole.FRUIT_SNACK,
            FoodRole.DAIRY_SNACK,
            FoodRole.FAT_SNACK
        )
    }

    private fun containsAny(
        text: String,
        keywords: Set<String>
    ): Boolean {
        return keywords.any(text::contains)
    }

    private fun isBreakfastGrain(food: Food): Boolean {
        val text = searchableFoodText(food)
        return foodRole(food) == FoodRole.GRAIN_SIDE && containsAny(text, breakfastGrainKeywords)
    }

    private fun isBreakfastProtein(food: Food): Boolean {
        val text = searchableFoodText(food)
        return foodRole(food) == FoodRole.DAIRY_SNACK || containsAny(text, breakfastProteinKeywords)
    }

    private fun isSavoryProtein(food: Food): Boolean {
        val text = searchableFoodText(food)
        return foodRole(food) == FoodRole.PROTEIN_MAIN &&
            containsAny(text, savoryProteinKeywords) &&
            !isBreakfastProtein(food)
    }

    private fun searchableFoodText(food: Food): String {
        return "${food.id} ${food.name} ${food.category}".lowercase()
    }

    private fun buildWeatherContext(
        recommendation: WeatherFoodRecommendation
    ): SmartCoachWeatherContext {
        val title = when (recommendation.focus) {
            WeatherNutritionFocus.HEAT_HYDRATION -> "Погодный фактор: жара"
            WeatherNutritionFocus.HEAT_ELECTROLYTES -> "Погодный фактор: жара и влажность"
            WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D -> "Погодный фактор: мало солнца"
            WeatherNutritionFocus.COLD_WARM_ENERGY -> "Погодный фактор: холод"
            WeatherNutritionFocus.RAINY_DAY_STABILITY -> "Погодный фактор: дождь"
            WeatherNutritionFocus.WIND_WARM_BALANCE -> "Погодный фактор: ветер"
            null -> "Погодный фактор"
        }
        val foodName = recommendation.food?.name
        val message = if (foodName != null) {
            "$foodName учитывается как контекстная подсказка: рекомендация не только по КБЖУ, но и по погоде за окном."
        } else {
            recommendation.headline
        }
        return SmartCoachWeatherContext(
            title = title,
            message = message,
            suggestedFood = recommendation.food
        )
    }

    private fun buildBalanceTitle(
        dailyNutrition: DailyNutrition,
        forecast: SmartCoachForecast,
        targets: NutritionTargets,
        focus: SmartCoachFocus,
        score: Int
    ): String {
        val calorieRatio = ratio(forecast.projectedCalories.toDouble(), targets.targetCalories.toDouble())
        val proteinRatio = ratio(forecast.projectedProtein.toDouble(), targets.proteinGrams.toDouble())
        val fatRatio = ratio(forecast.projectedFat.toDouble(), targets.fatGrams.toDouble())
        val carbsRatio = ratio(forecast.projectedCarbs.toDouble(), targets.carbsGrams.toDouble())

        return when {
            dailyNutrition.mealsCount == 0 -> "День еще не начат"
            fatRatio > 1.15 -> "Жиры выше плана"
            calorieRatio > 1.12 -> "Калорийность выше плана"
            carbsRatio > 1.15 -> "Углеводы выше плана"
            calorieRatio < 0.55 && proteinRatio < 0.60 -> "Мало энергии и белка"
            proteinRatio < 0.60 -> "Белка заметно не хватает"
            proteinRatio < 0.82 -> "Белок ниже плана"
            calorieRatio < 0.70 -> "Калорийность ниже плана"
            carbsRatio < 0.62 && focus == SmartCoachFocus.CARBS_DEFICIT -> "Мало углеводной энергии"
            score >= 86 -> "Баланс дня высокий"
            score >= 72 -> "День под контролем"
            score >= 56 -> "Нужна мягкая коррекция"
            else -> "Нужна точечная корректировка"
        }
    }

    private fun buildBalanceMessage(
        forecast: SmartCoachForecast,
        targets: NutritionTargets,
        focus: SmartCoachFocus
    ): String {
        val calorieDelta = forecast.projectedCalories - targets.targetCalories
        val fatDelta = forecast.projectedFat - targets.fatGrams
        val carbsDelta = forecast.projectedCarbs - targets.carbsGrams
        val proteinDelta = forecast.projectedProtein - targets.proteinGrams
        val deviations = buildList {
            if (abs(calorieDelta) > targets.targetCalories * 0.08) {
                add(if (calorieDelta > 0) "калории выше на ${abs(calorieDelta)} ккал" else "калории ниже на ${abs(calorieDelta)} ккал")
            }
            if (proteinDelta < -targets.proteinGrams * 0.12) {
                add("белка не хватает примерно ${abs(proteinDelta)} г")
            }
            if (fatDelta > targets.fatGrams * 0.10) {
                add("жиры выше примерно на ${abs(fatDelta)} г")
            }
            if (carbsDelta < -targets.carbsGrams * 0.18) {
                add("углеводов не хватает примерно ${abs(carbsDelta)} г")
            } else if (carbsDelta > targets.carbsGrams * 0.12) {
                add("углеводы выше примерно на ${abs(carbsDelta)} г")
            }
        }
        val focusText = when (focus) {
            SmartCoachFocus.PROTEIN_DEFICIT -> "Главная точка внимания сейчас - белок."
            SmartCoachFocus.CALORIES_EXCESS -> "Лучше выбирать легкие продукты без лишней калорийности."
            SmartCoachFocus.CALORIES_DEFICIT -> "Можно добавить более питательный прием пищи."
            SmartCoachFocus.CARBS_DEFICIT -> "Рациону не хватает углеводной энергии."
            SmartCoachFocus.FAT_DEFICIT -> "Рациону не хватает жиров."
            SmartCoachFocus.BALANCED -> "Критичных отклонений не видно."
        }
        val deviationText = if (deviations.isEmpty()) {
            "прогноз близок к цели"
        } else {
            deviations.joinToString("; ")
        }
        return "По прогнозу: $deviationText. $focusText"
    }

    private fun buildCorrectionTitle(focus: SmartCoachFocus): String {
        return when (focus) {
            SmartCoachFocus.CALORIES_DEFICIT -> "Что добавить сейчас"
            SmartCoachFocus.CALORIES_EXCESS -> "Как разгрузить остаток дня"
            SmartCoachFocus.PROTEIN_DEFICIT -> "Как закрыть белок"
            SmartCoachFocus.FAT_DEFICIT -> "Как добрать жиры"
            SmartCoachFocus.CARBS_DEFICIT -> "Как добрать углеводы"
            SmartCoachFocus.BALANCED -> "Как удержать баланс"
        }
    }

    private fun buildCorrectionMessage(
        focus: SmartCoachFocus,
        recommendation: FoodRecommendation?,
        weatherContext: SmartCoachWeatherContext?
    ): String {
        val suggestedFood = recommendation?.food
        val foodPrefix = suggestedFood?.let { "${it.name} - хороший вариант: " } ?: ""
        val base = when (focus) {
            SmartCoachFocus.PROTEIN_DEFICIT ->
                "${foodPrefix}поможет поднять белок без случайного выбора продукта."
            SmartCoachFocus.CALORIES_EXCESS ->
                "${foodPrefix}лучше подойдет для остатка дня, потому что не перегружает калорийность."
            SmartCoachFocus.CALORIES_DEFICIT ->
                "${foodPrefix}поможет закрыть оставшуюся калорийность и не пропустить прием пищи."
            SmartCoachFocus.FAT_DEFICIT ->
                "${foodPrefix}поможет мягко добрать жиры и сохранить общий баланс."
            SmartCoachFocus.CARBS_DEFICIT ->
                "${foodPrefix}даст углеводную энергию и поможет закрыть дневную норму."
            SmartCoachFocus.BALANCED ->
                "${foodPrefix}подходит как спокойный выбор, который не ломает текущий баланс."
        }
        val nutritionReason = suggestedFood?.let { food ->
            "На 100 г: ${food.caloriesPer100g.roundToInt()} ккал, " +
                "${formatAmount(food.proteinPer100g)} г белка, " +
                "${formatAmount(food.fatPer100g)} г жиров и " +
                "${formatAmount(food.carbsPer100g)} г углеводов."
        }
        val rankingReason = recommendation?.let {
            it.secondaryReason ?: it.primaryReason
        }
        val scoreReason = recommendation?.breakdown?.let { breakdown ->
            "Учтены остаток КБЖУ ${score10Text(breakdown.macroGapScore)}, порция ${score10Text(breakdown.portionPracticalityScore)} и уместность сейчас ${score10Text(breakdown.mealTimingScore)}."
        }
        return listOfNotNull(
            base,
            nutritionReason,
            scoreReason,
            rankingReason,
            weatherContext?.let { "${it.title.lowercase()}: ${it.message}" }
        ).joinToString(" ")
    }

    private fun buildReplacementReason(
        original: Food,
        replacement: Food,
        calorieDelta: Int,
        proteinDelta: Int,
        goal: UserGoal,
        focus: SmartCoachFocus,
        semanticMatch: String
    ): String {
        val calorieText = when {
            calorieDelta < 0 -> "на ${abs(calorieDelta)} ккал легче"
            calorieDelta > 0 -> "на $calorieDelta ккал питательнее"
            else -> "с похожей калорийностью"
        }
        val proteinText = when {
            proteinDelta > 0 -> "и дает на $proteinDelta г белка больше"
            proteinDelta < 0 -> "и дает на ${abs(proteinDelta)} г белка меньше"
            else -> "и сохраняет белок на том же уровне"
        }
        val goalText = "$semanticMatch " + when {
            focus == SmartCoachFocus.CALORIES_EXCESS || goal == UserGoal.WEIGHT_LOSS ->
                "Это помогает не перегружать остаток дня."
            focus == SmartCoachFocus.PROTEIN_DEFICIT || goal == UserGoal.MUSCLE_GAIN_TRAINING ->
                "Это лучше поддерживает белковую цель."
            else -> "Это делает выбор более сбалансированным."
        }
        return "Вместо ${original.name} можно выбрать ${replacement.name}: продукт $calorieText $proteinText. $goalText"
    }

    private fun buildScoreDetails(
        dailyNutrition: DailyNutrition,
        targets: NutritionTargets,
        recommendation: FoodRecommendation?,
        replacement: SmartCoachReplacement?,
        weatherContext: SmartCoachWeatherContext?,
        balanceScore: Int
    ): List<SmartCoachScoreDetail> {
        val details = mutableListOf<SmartCoachScoreDetail>()
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.DAY_STATE,
            label = "Баланс дня",
            value = balanceScore,
            description = "совпадение прогноза дня с целевыми КБЖУ"
        )
        if (dailyNutrition.mealsCount == 0) {
            details += SmartCoachScoreDetail(
                section = SmartCoachScoreSection.DAY_STATE,
                label = "Записи дня",
                value = 0,
                description = "день пока пустой, поэтому фактическая аналитика появится после первого приема пищи"
            )
        } else {
            details += SmartCoachScoreDetail(
                section = SmartCoachScoreSection.DAY_STATE,
                label = "Заполненность",
                value = dailyFillScore(dailyNutrition, targets),
                description = "доля уже закрытых калорий, белков, жиров и углеводов относительно дневной цели"
            )
        }

        if (recommendation == null) {
            details += SmartCoachScoreDetail(
                section = SmartCoachScoreSection.RECOMMENDATION,
                label = "Данные",
                value = 35,
                description = "нужно больше записей дневника для точного подбора рекомендации"
            )
            return details
        }

        val breakdown = recommendation.breakdown
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Попадание в остаток",
            value = breakdown.macroGapScore,
            description = if (dailyNutrition.mealsCount == 0) {
                "продукт сравнен с полной дневной нормой, так как записей за день еще нет"
            } else {
                "насколько продукт закрывает текущий остаток КБЖУ"
            }
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Практичность порции",
            value = breakdown.portionPracticalityScore,
            description = "оценивает, можно ли закрыть потребность нормальной порцией без завышения граммовки"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Уместность сейчас",
            value = breakdown.mealTimingScore,
            description = "учитывает текущий прием пищи, время дня и пищевую роль продукта"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Соответствие цели",
            value = breakdown.goalFitScore,
            description = "соответствие снижению, поддержанию или набору"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Уверенность",
            value = breakdown.confidenceScore,
            description = "чем больше данных о профиле, дневнике, избранном и истории, тем надежнее рекомендация"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Привычность",
            value = (breakdown.historyScore + breakdown.preferenceScore).coerceIn(0, 100),
            description = "история питания, избранное и пользовательские продукты"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Свежесть выбора",
            value = ((breakdown.varietyScore * 0.65) + (breakdown.roleBalanceScore * 0.35))
                .roundToInt()
                .coerceIn(0, 100),
            description = "учитывает повторяемость продукта и его роль в рационе"
        )
        if (replacement != null) {
            details += SmartCoachScoreDetail(
                section = SmartCoachScoreSection.CONTEXT,
                label = "Близость замены",
                value = replacement.semanticScore,
                description = replacement.semanticMatch
            )
        }
        if (weatherContext != null) {
            details += SmartCoachScoreDetail(
                section = SmartCoachScoreSection.CONTEXT,
                label = "Погода",
                value = 72,
                description = "внешний контекст добавлен к объяснению выбора"
            )
        }
        if (breakdown.safetyPenalty > 0) {
            details += SmartCoachScoreDetail(
                section = SmartCoachScoreSection.CONTEXT,
                label = "Безопасность",
                value = (100 - breakdown.safetyPenalty).coerceIn(0, 100),
                description = "учтен возможный аллергенный риск"
            )
        }
        return details.take(8)
    }

    private fun dailyFillScore(
        nutrition: DailyNutrition,
        targets: NutritionTargets
    ): Int {
        val ratios = listOf(
            nutrition.totalCalories / targets.targetCalories.coerceAtLeast(1).toDouble(),
            nutrition.totalProtein / targets.proteinGrams.coerceAtLeast(1).toDouble(),
            nutrition.totalFat / targets.fatGrams.coerceAtLeast(1).toDouble(),
            nutrition.totalCarbs / targets.carbsGrams.coerceAtLeast(1).toDouble()
        ).map { it.coerceIn(0.0, 1.0) }
        return (ratios.average() * 100).roundToInt().coerceIn(0, 100)
    }

    private fun formatAmount(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
    }

    private fun score10Text(score: Int): String {
        return "${(score / 10.0).roundToInt().coerceIn(0, 10)}/10"
    }

    private fun buildExplanationBullets(
        recommendation: FoodRecommendation?,
        focus: SmartCoachFocus,
        weatherContext: SmartCoachWeatherContext?
    ): List<String> {
        if (recommendation == null) {
            return listOf(
                "Учитываются цель, дневная норма и уже съеденные КБЖУ.",
                "После появления подходящих продуктов система предложит точную коррекцию."
            )
        }

        val breakdown = recommendation.breakdown
        val bullets = mutableListOf<String>()
        bullets += "Попадание в остаток: ${score10Text(breakdown.macroGapScore)} - продукт сравнен с текущим остатком КБЖУ, а не с уже съеденным рационом."
        bullets += "Практичность порции: ${score10Text(breakdown.portionPracticalityScore)} - проверяется, можно ли получить пользу нормальной порцией без искусственного увеличения граммовки."
        bullets += "Уместность сейчас: ${score10Text(breakdown.mealTimingScore)} - учитывается прием пищи и тип продукта."
        bullets += "Соответствие цели: ${score10Text(breakdown.goalFitScore)} - оценено соответствие выбранной цели пользователя."
        if (breakdown.confidenceScore < 65) {
            bullets += "Уверенность: ${score10Text(breakdown.confidenceScore)} - оценка станет точнее после новых записей дневника."
        }
        if (breakdown.historyScore + breakdown.preferenceScore > 55) {
            bullets += "Привычки: учтены история питания, избранное и пользовательские продукты."
        }
        if (breakdown.varietyScore >= 70) {
            bullets += "Разнообразие: продукт помогает не повторять одно и то же слишком часто."
        }
        if (breakdown.safetyPenalty > 0) {
            bullets += "Безопасность: есть только предполагаемый аллергенный риск, поэтому применен штраф."
        }
        if (weatherContext != null) {
            bullets += "Погода: рекомендация дополнена внешним контекстом дня."
        }
        if (focus == SmartCoachFocus.BALANCED) {
            bullets += "Баланс: критичных отклонений от плана сейчас не обнаружено."
        }
        return bullets.take(5)
    }

    private fun adherenceScore(value: Double, target: Double): Int {
        if (target <= 0.0) return 100
        val diff = abs(value / target - 1.0)
        return (100 - diff * 135).roundToInt().coerceIn(0, 100)
    }

    private fun ratio(value: Double, target: Double): Double {
        if (target <= 0.0) return 0.0
        return value / target
    }

    private fun lowerBoundScore(value: Double, target: Double): Int {
        if (target <= 0.0) return 100
        val ratio = value / target
        return when {
            ratio >= 1.0 -> (100 - (ratio - 1.0) * 45).roundToInt().coerceIn(72, 100)
            else -> (ratio * 100).roundToInt().coerceIn(0, 100)
        }
    }

    private fun estimateEatingDayProgress(nowMillis: Long): Double {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val hourValue = hour + minute / 60.0
        return ((hourValue - 7.0) / 16.0).coerceIn(0.18, 1.0)
    }

    private fun project(value: Double, progress: Double): Int {
        if (value <= 0.0) return 0
        return (value / progress).roundToInt().coerceAtLeast(0)
    }

    private fun inverseCaloriesScore(food: Food): Int {
        return (100 - ((food.caloriesPer100g - 60.0) / 300.0 * 100))
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun resolveSuggestedMealType(nowMillis: Long): MealType {
        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }
            .get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> MealType.BREAKFAST
            in 11..14 -> MealType.LUNCH
            in 15..17 -> MealType.SNACK
            in 18..21 -> MealType.DINNER
            else -> MealType.LATE_DINNER
        }
    }

    private data class RemainingNutrition(
        val calories: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double
    )

    private data class MealPlanTarget(
        val calories: Int,
        val protein: Int,
        val fat: Int,
        val carbs: Int
    )

    private data class PlanNutrition(
        val calories: Int,
        val protein: Int,
        val fat: Int,
        val carbs: Int
    )

    private data class PlanPracticalRange(
        val minGrams: Double,
        val maxGrams: Double,
        val defaultGrams: Double
    )

    private data class MealPlanPairCandidate(
        val first: FoodRecommendation,
        val second: FoodRecommendation,
        val compatibilityScore: Int
    )

    private enum class FoodRole {
        PROTEIN_MAIN,
        GRAIN_SIDE,
        LIGHT_PRODUCE,
        FRUIT_SNACK,
        DAIRY_SNACK,
        SWEET_SNACK,
        FAT_SNACK,
        BEVERAGE,
        SAUCE_OR_EXTRA,
        CUSTOM_RECIPE,
        OTHER
    }

    private companion object {
        val sweetKeywords = setOf(
            "chocolate",
            "cookie",
            "ice_cream",
            "honey",
            "jam",
            "sugar",
            "cake",
            "dessert",
            "sweet",
            "шоколад",
            "печенье",
            "морожен",
            "мед",
            "мёд",
            "варенье",
            "сахар",
            "десерт"
        )

        val beverageKeywords = setOf(
            "juice",
            "coffee",
            "tea",
            "drink",
            "сок",
            "кофе",
            "чай",
            "напит"
        )

        val sauceKeywords = setOf(
            "sauce",
            "ketchup",
            "mayonnaise",
            "oil",
            "соус",
            "кетчуп",
            "майонез",
            "масло"
        )

        val breakfastGrainKeywords = setOf(
            "oat",
            "oatmeal",
            "porridge",
            "cereal",
            "flakes",
            "granola",
            "muesli",
            "овсян",
            "каша",
            "хлоп",
            "гранола",
            "мюсли"
        )

        val breakfastProteinKeywords = setOf(
            "egg",
            "omelet",
            "omelette",
            "cottage",
            "yogurt",
            "yoghurt",
            "skyr",
            "kefir",
            "milk",
            "творог",
            "йогурт",
            "кефир",
            "молоко",
            "яйц",
            "омлет"
        )

        val savoryProteinKeywords = setOf(
            "beef",
            "chicken",
            "turkey",
            "pork",
            "meat",
            "fish",
            "salmon",
            "cod",
            "tuna",
            "trout",
            "говяд",
            "кур",
            "индей",
            "свинин",
            "мяс",
            "рыб",
            "лосос",
            "треск",
            "тунец",
            "форел"
        )
    }

    private fun endOfDay(dayStart: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }
}
