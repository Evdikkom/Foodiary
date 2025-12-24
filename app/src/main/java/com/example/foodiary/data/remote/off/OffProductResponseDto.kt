package com.example.foodiary.data.remote.off

import com.google.gson.annotations.SerializedName

data class OffProductResponseDto(
    @SerializedName("code") val code: String? = null,
    @SerializedName("status") val status: Int? = null,
    @SerializedName("status_verbose") val statusVerbose: String? = null,
    @SerializedName("product") val product: OffProductDto? = null
)

data class OffProductDto(
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("nutriments") val nutriments: OffNutrimentsDto? = null,
    @SerializedName("last_modified_t") val lastModifiedT: Long? = null
)

data class OffNutrimentsDto(
    // OFF обычно хранит значения на 100g в полях *_100g
    @SerializedName("energy-kcal_100g") val kcal100g: Double? = null,
    @SerializedName("proteins_100g") val proteins100g: Double? = null,
    @SerializedName("fat_100g") val fat100g: Double? = null,
    @SerializedName("carbohydrates_100g") val carbs100g: Double? = null
)
