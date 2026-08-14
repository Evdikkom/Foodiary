package com.example.foodiary.domain.model

enum class FoodImpactTone {
    POSITIVE,
    NEUTRAL,
    WARNING,
    DANGER
}

data class FoodImpactMacroStatus(
    val label: String,
    val before: Double,
    val after: Double,
    val target: Double,
    val unit: String,
    val tone: FoodImpactTone,
    val message: String
)

data class FoodImpactDetail(
    val label: String,
    val value: String,
    val description: String,
    val tone: FoodImpactTone = FoodImpactTone.NEUTRAL
)

data class FoodImpactPreview(
    val score: Int,
    val title: String,
    val summary: String,
    val portionAdvice: String,
    val recommendedQuantityInGrams: Double?,
    val projectedCalories: Double,
    val projectedProtein: Double,
    val projectedFat: Double,
    val projectedCarbs: Double,
    val macroStatuses: List<FoodImpactMacroStatus>,
    val details: List<FoodImpactDetail>
)
