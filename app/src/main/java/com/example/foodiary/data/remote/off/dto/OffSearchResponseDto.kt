package com.example.foodiary.data.remote.off.dto

import com.google.gson.annotations.SerializedName

data class OffSearchResponseDto(
    @SerializedName("count") val count: Int? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("page_size") val pageSize: Int? = null,
    @SerializedName("products") val products: List<OffSearchProductDto> = emptyList()
)

data class OffSearchProductDto(
    @SerializedName("code") val code: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("brands") val brands: String? = null,
    @SerializedName("nutriments") val nutriments: OffNutrimentsDto? = null
)

data class OffNutrimentsDto(
    @SerializedName("energy-kcal_100g") val kcal100g: Double? = null,
    @SerializedName("proteins_100g") val proteins100g: Double? = null,
    @SerializedName("fat_100g") val fat100g: Double? = null,
    @SerializedName("carbohydrates_100g") val carbs100g: Double? = null
)
