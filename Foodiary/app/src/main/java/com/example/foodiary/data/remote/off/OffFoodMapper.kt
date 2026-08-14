package com.example.foodiary.data.remote.off

import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.remote.off.dto.OffSearchProductDto
import com.example.foodiary.domain.model.FoodSearchItem

object OffFoodMapper {

    fun toFoodEntity(barcode: String, dto: OffProductDto): FoodEntity {
        val nutr = dto.nutriments

        val calories = normalizeCalories(
            kcal100g = nutr?.kcal100g,
            kj100g = nutr?.kj100g
        ) ?: 0.0
        val protein = nutr?.proteins100g ?: 0.0
        val fat = nutr?.fat100g ?: 0.0
        val carbs = nutr?.carbs100g ?: 0.0

        val imageUrl = normalizeImageUrl(dto.imageFrontSmallUrl?.trim()?.ifBlank {
            dto.imageFrontUrl?.trim()
        } ?: dto.imageFrontUrl?.trim())

        return FoodEntity(
            id = "off_$barcode",
            name = dto.productName?.takeIf { it.isNotBlank() } ?: "Продукт $barcode",
            imageUrl = imageUrl,
            caloriesPer100g = calories,
            proteinPer100g = protein,
            fatPer100g = fat,
            carbsPer100g = carbs
        )
    }

    fun toSearchItem(dto: OffSearchProductDto): FoodSearchItem? {
        val code = dto.code?.trim().orEmpty()
        val name = dto.productName?.trim().orEmpty()
        if (code.isBlank() || name.isBlank()) return null

        return FoodSearchItem(
            barcode = code,
            name = name,
            brand = dto.brands?.trim(),
            imageUrl = normalizeImageUrl(dto.imageFrontSmallUrl?.trim()?.ifBlank {
                dto.imageFrontUrl?.trim()
            } ?: dto.imageFrontUrl?.trim()),
            caloriesPer100g = normalizeCalories(
                kcal100g = dto.nutriments?.kcal100g,
                kj100g = dto.nutriments?.kj100g
            ),
            proteinPer100g = dto.nutriments?.proteins100g,
            fatPer100g = dto.nutriments?.fat100g,
            carbsPer100g = dto.nutriments?.carbs100g,
            allergenTags = dto.allergensTags.orEmpty(),
            traceTags = dto.tracesTags.orEmpty()
        )
    }

    private fun normalizeCalories(
        kcal100g: Double?,
        kj100g: Double?
    ): Double? {
        val kcalCandidate = kcal100g?.takeIf { it > 0.0 }
        val kjCandidate = kj100g?.takeIf { it > 0.0 }

        return when {
            kcalCandidate != null && kcalCandidate <= MAX_REASONABLE_KCAL_PER_100G -> kcalCandidate
            kcalCandidate != null -> kcalCandidate / KJ_PER_KCAL
            kjCandidate != null -> kjCandidate / KJ_PER_KCAL
            else -> null
        }
    }

    internal fun normalizeImageUrl(url: String?): String? {
        val normalized = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return normalized
            .replace(
                oldValue = "https://images.openfoodfacts.net/",
                newValue = "https://images.openfoodfacts.org/",
                ignoreCase = true
            )
    }

    private const val KJ_PER_KCAL = 4.184
    private const val MAX_REASONABLE_KCAL_PER_100G = 950.0
}
