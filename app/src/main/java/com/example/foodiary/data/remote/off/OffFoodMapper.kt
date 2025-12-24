package com.example.foodiary.data.remote.off

import com.example.foodiary.data.local.entity.FoodEntity
import kotlin.math.roundToInt

object OffFoodMapper {

    fun toFoodEntity(barcode: String, dto: OffProductDto): FoodEntity {
        val nutr = dto.nutriments

        val calories = nutr?.kcal100g ?: 0.0
        val protein = nutr?.proteins100g ?: 0.0
        val fat = nutr?.fat100g ?: 0.0
        val carbs = nutr?.carbs100g ?: 0.0

        // ВАЖНО: вы ранее решили “убираем foodId полностью” в части Meal,
        // но FoodEntity как справочник продуктов сохраняем — поэтому id делаем от barcode.
        return FoodEntity(
            id = "off_$barcode",
            name = dto.productName?.takeIf { it.isNotBlank() } ?: "Продукт $barcode",
            caloriesPer100g = calories,
            proteinPer100g = protein,
            fatPer100g = fat,
            carbsPer100g = carbs,
            // если у вас есть поля category/isCustom/externalId — заполните их здесь
            // externalId = barcode,
            // isCustom = false,
        )
    }
}
