package com.example.foodiary.domain.model

/**
 * Основание, по которому рассчитывается целевой белок.
 */
enum class ProteinGoalBasis {
    TOTAL_BODY_WEIGHT,
    ADJUSTED_BODY_WEIGHT,
    LEAN_BODY_MASS
}

/**
 * Рассчитанные суточные цели пользователя.
 *
 * Здесь хранятся и целевые макронутриенты, и объясняющие поля, чтобы
 * приложение могло прозрачно показывать пользователю, откуда взялся результат.
 */
data class NutritionTargets(
    val maintenanceCalories: Int,
    val targetCalories: Int,
    val proteinGrams: Int,
    val fatGrams: Int,
    val carbsGrams: Int,
    val proteinGoalBasis: ProteinGoalBasis,
    val proteinReferenceWeightKg: Double,
    val leanBodyMassKg: Double? = null,
    val adjustedBodyWeightKg: Double? = null,
    val bodyFatPercentUsed: Double? = null,
    val calorieDeltaFromMaintenance: Int = 0
)
