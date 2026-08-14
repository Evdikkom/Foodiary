package com.example.foodiary.domain.model

data class RecommendationScoreBreakdown(
    val historyScore: Int,
    val preferenceScore: Int,
    val goalFitScore: Int,
    val macroGapScore: Int,
    val varietyScore: Int,
    val safetyPenalty: Int,
    val confidenceScore: Int = 50,
    val portionPracticalityScore: Int = 70,
    val mealTimingScore: Int = 70,
    val roleBalanceScore: Int = 70
)

data class FoodRecommendation(
    val food: Food,
    val totalScore: Int,
    val primaryReason: String,
    val secondaryReason: String? = null,
    val breakdown: RecommendationScoreBreakdown
)
