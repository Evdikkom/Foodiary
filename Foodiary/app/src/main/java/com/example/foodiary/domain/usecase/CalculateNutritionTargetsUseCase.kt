package com.example.foodiary.domain.usecase

import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.BiologicalSex
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.ProteinGoalBasis
import com.example.foodiary.domain.model.User
import kotlin.math.roundToInt

/**
 * Научно обоснованный калькулятор суточных целей Foodiary v1.
 *
 * Что здесь реализовано:
 * 1. Калории поддержания считаются по официальным формулам
 *    Dietary Reference Intakes for Energy (2023) для взрослых 19+.
 * 2. Для похудения используется дефицит 500 килокалорий в день,
 *    но не ниже практических минимальных рамок из клинических гайдлайнов:
 *    1200 килокалорий для женщин и 1500 килокалорий для мужчин.
 * 3. Белок считается:
 *    - по безжировой массе тела, если пользователь знает процент жира;
 *    - по скорректированной массе тела при ожирении, если процента жира нет;
 *    - по общей массе тела в остальных случаях.
 * 4. Для обычных режимов жиры стартово задаются как 30% калорий.
 * 5. Для набора мышечной массы при силовых тренировках используется:
 *    - осторожный динамический профицит, зависящий от массы тела;
 *    - более высокий белок;
 *    - доля жиров 25%, чтобы оставить больше энергии под углеводы.
 * 6. Углеводы получают оставшиеся калории, но стремятся не опускаться
 *    ниже 130 граммов в день.
 */
class CalculateNutritionTargetsUseCase {

    operator fun invoke(user: User): NutritionTargets {
        val maintenanceCalories = calculateEstimatedEnergyRequirement(user)
        val targetCalories = calculateGoalCalories(user, maintenanceCalories)

        val proteinBasis = resolveProteinBasis(user)
        val proteinFactor = resolveProteinFactor(user)
        val proteinGrams = (proteinBasis.referenceWeightKg * proteinFactor)
            .roundToInt()
            .coerceAtLeast(0)

        val preferredFatShare = resolvePreferredFatShare(user)
        val preferredFatGrams = ((targetCalories * preferredFatShare) / KCAL_PER_GRAM_FAT)
            .roundToInt()
            .coerceAtLeast(0)

        val proteinCalories = proteinGrams * KCAL_PER_GRAM_PROTEIN
        var fatGrams = preferredFatGrams
        var carbsGrams = (
            (targetCalories - proteinCalories - fatGrams * KCAL_PER_GRAM_FAT) /
                KCAL_PER_GRAM_CARBS.toDouble()
            )
            .roundToInt()
            .coerceAtLeast(0)

        if (carbsGrams < MIN_CARBS_GRAMS) {
            carbsGrams = MIN_CARBS_GRAMS
            fatGrams = (
                (targetCalories - proteinCalories - carbsGrams * KCAL_PER_GRAM_CARBS) /
                    KCAL_PER_GRAM_FAT.toDouble()
                )
                .roundToInt()
                .coerceAtLeast(0)
        }

        return NutritionTargets(
            maintenanceCalories = maintenanceCalories,
            targetCalories = targetCalories,
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            carbsGrams = carbsGrams,
            proteinGoalBasis = proteinBasis.basis,
            proteinReferenceWeightKg = proteinBasis.referenceWeightKg,
            leanBodyMassKg = proteinBasis.leanBodyMassKg,
            adjustedBodyWeightKg = proteinBasis.adjustedBodyWeightKg,
            bodyFatPercentUsed = proteinBasis.bodyFatPercentUsed,
            calorieDeltaFromMaintenance = targetCalories - maintenanceCalories
        )
    }

    private fun calculateEstimatedEnergyRequirement(user: User): Int {
        val age = user.age.toDouble()
        val height = user.heightCm.toDouble()
        val weight = user.weightKg

        val calories = when (user.biologicalSex) {
            BiologicalSex.MALE -> when (user.activityLevel) {
                ActivityLevel.INACTIVE ->
                    753.07 - (10.83 * age) + (6.50 * height) + (14.10 * weight)
                ActivityLevel.LOW_ACTIVE ->
                    581.47 - (10.83 * age) + (8.30 * height) + (14.94 * weight)
                ActivityLevel.ACTIVE ->
                    1004.82 - (10.83 * age) + (6.52 * height) + (15.91 * weight)
                ActivityLevel.VERY_ACTIVE ->
                    -517.88 - (10.83 * age) + (15.61 * height) + (19.11 * weight)
            }

            BiologicalSex.FEMALE -> when (user.activityLevel) {
                ActivityLevel.INACTIVE ->
                    584.90 - (7.01 * age) + (5.72 * height) + (11.71 * weight)
                ActivityLevel.LOW_ACTIVE ->
                    575.77 - (7.01 * age) + (6.60 * height) + (12.14 * weight)
                ActivityLevel.ACTIVE ->
                    710.25 - (7.01 * age) + (6.54 * height) + (12.34 * weight)
                ActivityLevel.VERY_ACTIVE ->
                    511.83 - (7.01 * age) + (9.07 * height) + (12.56 * weight)
            }
        }

        return calories.roundToInt()
    }

    private fun calculateGoalCalories(user: User, maintenanceCalories: Int): Int {
        return when (user.goal) {
            UserGoal.MAINTAIN_WEIGHT -> maintenanceCalories
            UserGoal.WEIGHT_LOSS -> {
                val floor = when (user.biologicalSex) {
                    BiologicalSex.FEMALE -> 1200
                    BiologicalSex.MALE -> 1500
                }
                maxOf(maintenanceCalories - 500, floor)
            }

            UserGoal.WEIGHT_GAIN -> maintenanceCalories + 250
            UserGoal.MUSCLE_GAIN_TRAINING -> {
                val weeklyGainKg = user.weightKg * MUSCLE_GAIN_WEEKLY_RATE
                val dailySurplus = ((weeklyGainKg * KCAL_PER_KILOGRAM_BODY_MASS_CHANGE) / DAYS_PER_WEEK)
                    .roundToInt()
                maintenanceCalories + dailySurplus
            }
        }
    }

    private fun resolveProteinFactor(user: User): Double {
        return when (user.goal) {
            UserGoal.MAINTAIN_WEIGHT -> if (user.age >= 60) 1.2 else 1.0
            UserGoal.WEIGHT_LOSS -> 1.2
            UserGoal.WEIGHT_GAIN -> 1.4
            UserGoal.MUSCLE_GAIN_TRAINING -> {
                if (hasValidBodyFatPercent(user)) 2.0 else 1.6
            }
        }
    }

    private fun resolvePreferredFatShare(user: User): Double {
        return when (user.goal) {
            UserGoal.MUSCLE_GAIN_TRAINING -> MUSCLE_GAIN_FAT_SHARE
            else -> DEFAULT_FAT_SHARE
        }
    }

    private fun resolveProteinBasis(user: User): ProteinBasisResolution {
        val normalizedBodyFat = user.bodyFatPercent?.takeIf { isValidBodyFatPercent(it) }
        if (normalizedBodyFat != null) {
            val leanBodyMassKg = user.weightKg * (1.0 - normalizedBodyFat / 100.0)
            return ProteinBasisResolution(
                basis = ProteinGoalBasis.LEAN_BODY_MASS,
                referenceWeightKg = leanBodyMassKg,
                leanBodyMassKg = leanBodyMassKg,
                bodyFatPercentUsed = normalizedBodyFat
            )
        }

        val heightMeters = user.heightCm / 100.0
        val bodyMassIndex = user.weightKg / (heightMeters * heightMeters)
        if (bodyMassIndex >= 30.0) {
            val referenceWeight = 25.0 * heightMeters * heightMeters
            val adjustedWeight = referenceWeight + (0.33 * (user.weightKg - referenceWeight))
            return ProteinBasisResolution(
                basis = ProteinGoalBasis.ADJUSTED_BODY_WEIGHT,
                referenceWeightKg = adjustedWeight,
                adjustedBodyWeightKg = adjustedWeight
            )
        }

        return ProteinBasisResolution(
            basis = ProteinGoalBasis.TOTAL_BODY_WEIGHT,
            referenceWeightKg = user.weightKg
        )
    }

    private fun hasValidBodyFatPercent(user: User): Boolean {
        return user.bodyFatPercent?.let(::isValidBodyFatPercent) == true
    }

    private fun isValidBodyFatPercent(value: Double): Boolean {
        return value in 3.0..75.0
    }

    private data class ProteinBasisResolution(
        val basis: ProteinGoalBasis,
        val referenceWeightKg: Double,
        val leanBodyMassKg: Double? = null,
        val adjustedBodyWeightKg: Double? = null,
        val bodyFatPercentUsed: Double? = null
    )

    private companion object {
        const val KCAL_PER_GRAM_PROTEIN = 4
        const val KCAL_PER_GRAM_CARBS = 4
        const val KCAL_PER_GRAM_FAT = 9

        const val DEFAULT_FAT_SHARE = 0.30
        const val MUSCLE_GAIN_FAT_SHARE = 0.25
        const val MIN_CARBS_GRAMS = 130
        const val MUSCLE_GAIN_WEEKLY_RATE = 0.0025
        const val KCAL_PER_KILOGRAM_BODY_MASS_CHANGE = 7700
        const val DAYS_PER_WEEK = 7
    }
}
