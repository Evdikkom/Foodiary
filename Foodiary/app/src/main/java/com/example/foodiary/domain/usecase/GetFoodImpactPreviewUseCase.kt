package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodImpactDetail
import com.example.foodiary.domain.model.FoodImpactMacroStatus
import com.example.foodiary.domain.model.FoodImpactPreview
import com.example.foodiary.domain.model.FoodImpactTone
import com.example.foodiary.domain.model.FoodSafetyProfile
import com.example.foodiary.domain.model.NutritionTargets
import kotlin.math.abs
import kotlin.math.roundToInt

class GetFoodImpactPreviewUseCase {

    operator fun invoke(
        food: Food,
        quantityInGrams: Double,
        currentNutrition: DailyNutrition,
        targets: NutritionTargets,
        safetyProfile: FoodSafetyProfile? = null,
        replacedFood: Food? = null,
        replacedQuantityInGrams: Double = 0.0
    ): FoodImpactPreview {
        val safeQuantity = quantityInGrams.coerceAtLeast(0.0)
        val baseline = currentNutrition.minusFood(replacedFood, replacedQuantityInGrams)
        val impact = food.asNutritionImpact(safeQuantity)

        val projectedCalories = baseline.totalCalories + impact.calories
        val projectedProtein = baseline.totalProtein + impact.protein
        val projectedFat = baseline.totalFat + impact.fat
        val projectedCarbs = baseline.totalCarbs + impact.carbs

        val caloriesStatus = buildMacroStatus(
            label = "Калории",
            before = baseline.totalCalories,
            after = projectedCalories,
            target = targets.targetCalories.toDouble(),
            unit = "ккал",
            lowerBoundOnly = false
        )
        val proteinStatus = buildMacroStatus(
            label = "Белки",
            before = baseline.totalProtein,
            after = projectedProtein,
            target = targets.proteinGrams.toDouble(),
            unit = "г",
            lowerBoundOnly = true
        )
        val fatStatus = buildMacroStatus(
            label = "Жиры",
            before = baseline.totalFat,
            after = projectedFat,
            target = targets.fatGrams.toDouble(),
            unit = "г",
            lowerBoundOnly = false
        )
        val carbsStatus = buildMacroStatus(
            label = "Углеводы",
            before = baseline.totalCarbs,
            after = projectedCarbs,
            target = targets.carbsGrams.toDouble(),
            unit = "г",
            lowerBoundOnly = false
        )

        val recommendedQuantity = calculateRecommendedQuantity(
            food = food,
            selectedQuantity = safeQuantity,
            baseline = baseline,
            targets = targets
        )
        val portionAdvice = buildPortionAdvice(
            food = food,
            selectedQuantity = safeQuantity,
            recommendedQuantity = recommendedQuantity,
            baseline = baseline,
            targets = targets
        )

        val safetyPenalty = when {
            safetyProfile?.hasHighRisk == true -> 45
            safetyProfile?.warningConflicts?.isNotEmpty() == true -> 20
            else -> 0
        }

        val score = (
            adherenceScore(projectedCalories, targets.targetCalories.toDouble()) * 0.32 +
                lowerBoundScore(projectedProtein, targets.proteinGrams.toDouble()) * 0.28 +
                adherenceScore(projectedFat, targets.fatGrams.toDouble()) * 0.14 +
                adherenceScore(projectedCarbs, targets.carbsGrams.toDouble()) * 0.12 +
                portionFitScore(safeQuantity, recommendedQuantity) * 0.14 -
                safetyPenalty
            ).roundToInt().coerceIn(0, 100)

        return FoodImpactPreview(
            score = score,
            title = buildTitle(
                score = score,
                safetyProfile = safetyProfile,
                selectedQuantity = safeQuantity,
                recommendedQuantity = recommendedQuantity,
                projectedCalories = projectedCalories,
                projectedProtein = projectedProtein,
                projectedFat = projectedFat,
                projectedCarbs = projectedCarbs,
                targets = targets
            ),
            summary = buildSummary(
                food = food,
                quantity = safeQuantity,
                projectedCalories = projectedCalories,
                projectedProtein = projectedProtein,
                projectedFat = projectedFat,
                projectedCarbs = projectedCarbs,
                targets = targets,
                safetyProfile = safetyProfile
            ),
            portionAdvice = portionAdvice,
            recommendedQuantityInGrams = recommendedQuantity,
            projectedCalories = projectedCalories,
            projectedProtein = projectedProtein,
            projectedFat = projectedFat,
            projectedCarbs = projectedCarbs,
            macroStatuses = listOf(caloriesStatus, proteinStatus, fatStatus, carbsStatus),
            details = buildDetails(
                impact = impact,
                targets = targets,
                projectedCalories = projectedCalories,
                projectedProtein = projectedProtein,
                safetyProfile = safetyProfile
            )
        )
    }

    private fun DailyNutrition.minusFood(
        food: Food?,
        quantityInGrams: Double
    ): DailyNutrition {
        if (food == null || quantityInGrams <= 0.0) return this
        val impact = food.asNutritionImpact(quantityInGrams)
        return copy(
            totalCalories = (totalCalories - impact.calories).coerceAtLeast(0.0),
            totalProtein = (totalProtein - impact.protein).coerceAtLeast(0.0),
            totalFat = (totalFat - impact.fat).coerceAtLeast(0.0),
            totalCarbs = (totalCarbs - impact.carbs).coerceAtLeast(0.0)
        )
    }

    private fun Food.asNutritionImpact(quantityInGrams: Double): NutritionImpact {
        val factor = quantityInGrams.coerceAtLeast(0.0) / 100.0
        return NutritionImpact(
            calories = caloriesPer100g * factor,
            protein = proteinPer100g * factor,
            fat = fatPer100g * factor,
            carbs = carbsPer100g * factor
        )
    }

    private fun buildMacroStatus(
        label: String,
        before: Double,
        after: Double,
        target: Double,
        unit: String,
        lowerBoundOnly: Boolean
    ): FoodImpactMacroStatus {
        val ratio = if (target > 0.0) after / target else 0.0
        val tone = when {
            lowerBoundOnly && ratio >= 0.85 -> FoodImpactTone.POSITIVE
            lowerBoundOnly && ratio >= 0.65 -> FoodImpactTone.NEUTRAL
            lowerBoundOnly -> FoodImpactTone.WARNING
            ratio <= 1.0 -> FoodImpactTone.POSITIVE
            ratio <= 1.10 -> FoodImpactTone.WARNING
            else -> FoodImpactTone.DANGER
        }
        val message = when {
            lowerBoundOnly && ratio >= 1.0 -> "цель закрыта"
            lowerBoundOnly -> "останется ${formatAmount((target - after).coerceAtLeast(0.0))} $unit до цели"
            ratio <= 1.0 -> "останется ${formatAmount((target - after).coerceAtLeast(0.0))} $unit"
            else -> "перебор на ${formatAmount(after - target)} $unit"
        }
        return FoodImpactMacroStatus(
            label = label,
            before = before,
            after = after,
            target = target,
            unit = unit,
            tone = tone,
            message = message
        )
    }

    private fun calculateRecommendedQuantity(
        food: Food,
        selectedQuantity: Double,
        baseline: DailyNutrition,
        targets: NutritionTargets
    ): Double? {
        if (selectedQuantity <= 0.0) return null

        val remainingCalories = targets.targetCalories - baseline.totalCalories
        if (food.caloriesPer100g <= 0.0) return null
        if (remainingCalories <= 0.0) return MIN_RECOMMENDED_PORTION_GRAMS

        val calorieLimitQuantity = (remainingCalories / food.caloriesPer100g) * 100.0
        val fatLimitQuantity = macroQuantityLimit(
            remaining = targets.fatGrams - baseline.totalFat,
            per100g = food.fatPer100g
        )
        val carbsLimitQuantity = macroQuantityLimit(
            remaining = targets.carbsGrams - baseline.totalCarbs,
            per100g = food.carbsPer100g
        )
        val macroSafeLimit = listOf(
            calorieLimitQuantity,
            fatLimitQuantity,
            carbsLimitQuantity
        )
            .filter { it.isFinite() && it > 0.0 }
            .minOrNull()
            ?: return null

        val safeUpper = minOf(
            macroSafeLimit,
            practicalPortionLimit(food.caloriesPer100g)
        ).coerceAtLeast(MIN_RECOMMENDED_PORTION_GRAMS)

        val remainingProtein = targets.proteinGrams - baseline.totalProtein
        val proteinTargetQuantity = if (
            remainingProtein > targets.proteinGrams * 0.15 &&
            food.proteinPer100g >= PROTEIN_FOCUSED_FOOD_THRESHOLD
        ) {
            (remainingProtein / food.proteinPer100g) * 100.0
        } else {
            null
        }

        val balancedQuantity = (calorieLimitQuantity * PORTION_CALORIE_SHARE)
            .coerceAtLeast(MIN_RECOMMENDED_PORTION_GRAMS)

        val targetQuantity = when {
            proteinTargetQuantity != null -> minOf(proteinTargetQuantity, safeUpper)
            else -> minOf(balancedQuantity, safeUpper)
        }

        return normalizeRecommendedPortion(targetQuantity)
    }

    private fun macroQuantityLimit(remaining: Double, per100g: Double): Double {
        if (per100g <= 0.0) return Double.POSITIVE_INFINITY
        if (remaining <= 0.0) return MIN_RECOMMENDED_PORTION_GRAMS
        return (remaining / per100g) * 100.0
    }

    private fun practicalPortionLimit(caloriesPer100g: Double): Double {
        return when {
            caloriesPer100g >= 450.0 -> 80.0
            caloriesPer100g >= 320.0 -> 120.0
            caloriesPer100g >= 220.0 -> 180.0
            caloriesPer100g >= 120.0 -> 260.0
            caloriesPer100g >= 70.0 -> 380.0
            else -> 450.0
        }
    }

    private fun normalizeRecommendedPortion(quantity: Double): Double {
        val rounded = (quantity / PORTION_ROUND_STEP_GRAMS).roundToInt() * PORTION_ROUND_STEP_GRAMS
        return rounded.coerceIn(MIN_RECOMMENDED_PORTION_GRAMS, MAX_RECOMMENDED_PORTION_GRAMS)
    }

    private fun buildPortionAdvice(
        food: Food,
        selectedQuantity: Double,
        recommendedQuantity: Double?,
        baseline: DailyNutrition,
        targets: NutritionTargets
    ): String {
        if (selectedQuantity <= 0.0) return "Укажите порцию, чтобы Foodiary рассчитал влияние продукта на день."
        if (recommendedQuantity == null) {
            return "Выбранная порция выглядит допустимой: после добавления рацион остается в рабочем диапазоне цели."
        }
        val rounded = recommendedQuantity.roundToInt()
        val tolerance = maxOf(10.0, recommendedQuantity * 0.08)
        val reason = buildPortionReason(food, baseline, targets)
        return when {
            abs(selectedQuantity - recommendedQuantity) <= tolerance ->
                "Порция около $rounded г подходит текущему дню: $reason"
            recommendedQuantity < selectedQuantity ->
                "Лучше выбрать около $rounded г вместо ${selectedQuantity.roundToInt()} г: $reason"
            else ->
                "Можно увеличить порцию примерно до $rounded г: $reason"
        }
    }

    private fun buildPortionReason(
        food: Food,
        baseline: DailyNutrition,
        targets: NutritionTargets
    ): String {
        val remainingCalories = (targets.targetCalories - baseline.totalCalories).coerceAtLeast(0.0)
        val remainingFat = (targets.fatGrams - baseline.totalFat).coerceAtLeast(0.0)
        val remainingCarbs = (targets.carbsGrams - baseline.totalCarbs).coerceAtLeast(0.0)

        val limitingFactor = listOf(
            "остатку калорий" to macroQuantityLimit(remainingCalories, food.caloriesPer100g),
            "остатку жиров" to macroQuantityLimit(remainingFat, food.fatPer100g),
            "остатку углеводов" to macroQuantityLimit(remainingCarbs, food.carbsPer100g)
        )
            .filter { (_, limit) -> limit.isFinite() && limit > 0.0 }
            .minByOrNull { (_, limit) -> limit }
            ?.first
            ?: "остатку дневной цели"

        val foodProfile = when {
            food.caloriesPer100g >= 320.0 ->
                "продукт калорийный"
            food.fatPer100g >= 15.0 ->
                "в продукте много жиров"
            food.carbsPer100g >= 35.0 && food.proteinPer100g < 10.0 ->
                "в продукте много углеводов и мало белка"
            food.proteinPer100g >= PROTEIN_FOCUSED_FOOD_THRESHOLD ->
                "продукт помогает добрать белок"
            else ->
                "продукт оценивается по балансу калорий, белков, жиров и углеводов"
        }

        return "$foodProfile, поэтому размер порции рассчитан по $limitingFactor с запасом на остальные приемы пищи."
    }

    private fun buildTitle(
        score: Int,
        safetyProfile: FoodSafetyProfile?,
        selectedQuantity: Double,
        recommendedQuantity: Double?,
        projectedCalories: Double,
        projectedProtein: Double,
        projectedFat: Double,
        projectedCarbs: Double,
        targets: NutritionTargets
    ): String {
        val calorieRatio = ratio(projectedCalories, targets.targetCalories.toDouble())
        val proteinRatio = ratio(projectedProtein, targets.proteinGrams.toDouble())
        val fatRatio = ratio(projectedFat, targets.fatGrams.toDouble())
        val carbsRatio = ratio(projectedCarbs, targets.carbsGrams.toDouble())

        return when {
            safetyProfile?.hasHighRisk == true -> "Есть риск по ограничениям"
            safetyProfile?.warningConflicts?.isNotEmpty() == true -> "Проверьте ограничения"
            recommendedQuantity != null && selectedQuantity > recommendedQuantity * 1.12 ->
                "Порцию лучше уменьшить"
            fatRatio > 1.15 -> "Порция перегружает жиры"
            calorieRatio > 1.10 -> "Может перегрузить день"
            carbsRatio > 1.12 -> "Углеводы выйдут выше плана"
            fatRatio > 1.03 -> "Жиры почти на пределе"
            projectedProtein >= targets.proteinGrams && score >= 70 -> "Хорошо закрывает белковую цель"
            proteinRatio < 0.55 && calorieRatio < 0.75 -> "Нужен еще один прием пищи"
            proteinRatio < 0.65 -> "Белок останется низким"
            calorieRatio < 0.55 -> "Калорий еще мало"
            score >= 82 -> "Хорошо вписывается в день"
            score >= 62 -> "Можно добавить осознанно"
            else -> "Подходит не идеально"
        }
    }

    private fun buildSummary(
        food: Food,
        quantity: Double,
        projectedCalories: Double,
        projectedProtein: Double,
        projectedFat: Double,
        projectedCarbs: Double,
        targets: NutritionTargets,
        safetyProfile: FoodSafetyProfile?
    ): String {
        val base = "${food.name}, ${quantity.roundToInt()} г: после добавления будет ${projectedCalories.roundToInt()} из ${targets.targetCalories} ккал и ${formatAmount(projectedProtein)} из ${targets.proteinGrams} г белка."
        return when {
            safetyProfile?.hasHighRisk == true ->
                "$base Есть конфликт с аллергенными ограничениями, поэтому сохранение требует дополнительного подтверждения."
            safetyProfile?.warningConflicts?.isNotEmpty() == true ->
                "$base Есть предупреждение по ограничениям, состав стоит перепроверить."
            projectedFat > targets.fatGrams * 1.10 ->
                "$base Главный риск этой порции - жиры: после добавления будет ${formatAmount(projectedFat)} из ${targets.fatGrams} г."
            projectedCalories > targets.targetCalories ->
                "$base Калории выйдут выше дневной цели, поэтому порцию лучше уменьшить или заменить продукт."
            projectedCarbs > targets.carbsGrams * 1.10 ->
                "$base Углеводы выйдут выше дневной цели, поэтому следующий выбор лучше сделать более легким по углеводам."
            projectedProtein < targets.proteinGrams * 0.75 ->
                "$base Белковая цель пока закрыта не полностью, следующий выбор лучше сделать более белковым."
            else -> "$base Продукт не нарушает основной дневной баланс."
        }
    }

    private fun buildDetails(
        impact: NutritionImpact,
        targets: NutritionTargets,
        projectedCalories: Double,
        projectedProtein: Double,
        safetyProfile: FoodSafetyProfile?
    ): List<FoodImpactDetail> {
        val details = mutableListOf<FoodImpactDetail>()
        details += FoodImpactDetail(
            label = "Вклад порции",
            value = "+${impact.calories.roundToInt()} ккал",
            description = "${formatAmount(impact.protein)} г белка, ${formatAmount(impact.fat)} г жиров, ${formatAmount(impact.carbs)} г углеводов"
        )
        details += FoodImpactDetail(
            label = "Калорийность",
            value = "${buildPercent(projectedCalories, targets.targetCalories.toDouble())}%",
            description = if (projectedCalories <= targets.targetCalories) {
                "дневная цель остается в пределах нормы"
            } else {
                "после добавления появляется превышение цели"
            },
            tone = if (projectedCalories <= targets.targetCalories) FoodImpactTone.POSITIVE else FoodImpactTone.WARNING
        )
        details += FoodImpactDetail(
            label = "Белковая цель",
            value = "${buildPercent(projectedProtein, targets.proteinGrams.toDouble())}%",
            description = if (projectedProtein >= targets.proteinGrams) {
                "продукт помогает закрыть белковую норму"
            } else {
                "часть белковой цели останется незакрытой"
            },
            tone = if (projectedProtein >= targets.proteinGrams) FoodImpactTone.POSITIVE else FoodImpactTone.NEUTRAL
        )
        if (safetyProfile?.hasHighRisk == true || safetyProfile?.warningConflicts?.isNotEmpty() == true) {
            details += FoodImpactDetail(
                label = "Ограничения",
                value = if (safetyProfile.hasHighRisk) "риск" else "проверить",
                description = "учтены аллергены и пользовательские ограничения",
                tone = if (safetyProfile.hasHighRisk) FoodImpactTone.DANGER else FoodImpactTone.WARNING
            )
        }
        return details
    }

    private fun adherenceScore(value: Double, target: Double): Double {
        if (target <= 0.0) return 50.0
        val diffRatio = abs(value - target) / target
        return (100.0 - diffRatio * 120.0).coerceIn(0.0, 100.0)
    }

    private fun lowerBoundScore(value: Double, target: Double): Double {
        if (target <= 0.0) return 50.0
        val ratio = value / target
        return when {
            ratio >= 1.0 -> 100.0
            else -> (ratio * 100.0).coerceIn(0.0, 100.0)
        }
    }

    private fun portionFitScore(
        selectedQuantity: Double,
        recommendedQuantity: Double?
    ): Double {
        if (selectedQuantity <= 0.0) return 0.0
        if (recommendedQuantity == null) return 100.0
        val diffRatio = abs(selectedQuantity - recommendedQuantity) / selectedQuantity
        return (100.0 - diffRatio * 100.0).coerceIn(35.0, 100.0)
    }

    private fun buildPercent(value: Double, target: Double): Int {
        if (target <= 0.0) return 0
        return ((value / target) * 100.0).roundToInt().coerceAtLeast(0)
    }

    private fun ratio(value: Double, target: Double): Double {
        if (target <= 0.0) return 0.0
        return value / target
    }

    private fun formatAmount(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
    }

    private companion object {
        const val MIN_RECOMMENDED_PORTION_GRAMS = 30.0
        const val MAX_RECOMMENDED_PORTION_GRAMS = 450.0
        const val PORTION_ROUND_STEP_GRAMS = 5.0
        const val PORTION_CALORIE_SHARE = 0.30
        const val PROTEIN_FOCUSED_FOOD_THRESHOLD = 8.0
    }

    private data class NutritionImpact(
        val calories: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double
    )
}
