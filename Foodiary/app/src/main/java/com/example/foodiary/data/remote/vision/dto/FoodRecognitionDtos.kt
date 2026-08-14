package com.example.foodiary.data.remote.vision.dto

import com.google.gson.annotations.SerializedName

data class AnalyzeFoodResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("image_id") val imageId: String? = null,
    @SerializedName("items") val items: List<DetectedDishItemDto> = emptyList(),
    @SerializedName("summary") val summary: NutritionSummaryDto? = null,
    @SerializedName("raw_dish_label") val rawDishLabel: String? = null,
    @SerializedName("notes") val notes: List<String> = emptyList()
)

data class DetectedDishItemDto(
    @SerializedName("item_index") val itemIndex: Int = 0,
    @SerializedName("top_candidate") val topCandidate: DishCandidateDto? = null,
    @SerializedName("candidates") val candidates: List<DishCandidateDto> = emptyList(),
    @SerializedName("serving_size") val servingSize: String? = null,
    @SerializedName("calories_kcal") val caloriesKcal: Double? = null,
    @SerializedName("protein_g") val proteinG: Double? = null,
    @SerializedName("fat_g") val fatG: Double? = null,
    @SerializedName("carbs_g") val carbsG: Double? = null,
    @SerializedName("ingredients") val ingredients: List<String> = emptyList()
)

data class DishCandidateDto(
    @SerializedName("class_id") val classId: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("confidence") val confidence: Double? = null
)

data class NutritionSummaryDto(
    @SerializedName("calories_kcal") val caloriesKcal: Double? = null,
    @SerializedName("protein_g") val proteinG: Double? = null,
    @SerializedName("fat_g") val fatG: Double? = null,
    @SerializedName("carbs_g") val carbsG: Double? = null
)
