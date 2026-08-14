package com.example.foodiary.data.local.preferences

import android.content.Context
import com.example.foodiary.domain.model.NutritionTargets
import kotlin.math.roundToInt

class NutritionTargetsPreferences(context: Context) {

    data class NutritionOverride(
        val calories: Int,
        val proteinPercent: Int,
        val fatPercent: Int,
        val carbsPercent: Int
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOverride(): NutritionOverride? {
        if (!prefs.contains(KEY_CALORIES)) return null
        return NutritionOverride(
            calories = prefs.getInt(KEY_CALORIES, 0),
            proteinPercent = prefs.getInt(KEY_PROTEIN_PERCENT, 20),
            fatPercent = prefs.getInt(KEY_FAT_PERCENT, 30),
            carbsPercent = prefs.getInt(KEY_CARBS_PERCENT, 50)
        )
    }

    fun saveOverride(override: NutritionOverride) {
        prefs.edit()
            .putInt(KEY_CALORIES, override.calories)
            .putInt(KEY_PROTEIN_PERCENT, override.proteinPercent)
            .putInt(KEY_FAT_PERCENT, override.fatPercent)
            .putInt(KEY_CARBS_PERCENT, override.carbsPercent)
            .apply()
    }

    fun clearOverride() {
        prefs.edit()
            .remove(KEY_CALORIES)
            .remove(KEY_PROTEIN_PERCENT)
            .remove(KEY_FAT_PERCENT)
            .remove(KEY_CARBS_PERCENT)
            .apply()
    }

    fun apply(base: NutritionTargets): NutritionTargets {
        val override = getOverride() ?: return base

        val calories = override.calories
        val proteinGrams = ((calories * (override.proteinPercent / 100.0)) / KCAL_PER_GRAM_PROTEIN)
            .roundToInt()
        val fatGrams = ((calories * (override.fatPercent / 100.0)) / KCAL_PER_GRAM_FAT)
            .roundToInt()
        val carbsGrams = ((calories * (override.carbsPercent / 100.0)) / KCAL_PER_GRAM_CARBS)
            .roundToInt()

        return base.copy(
            targetCalories = calories,
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            carbsGrams = carbsGrams,
            calorieDeltaFromMaintenance = calories - base.maintenanceCalories
        )
    }

    companion object {
        private const val PREFS_NAME = "foodiary_nutrition_targets"
        private const val KEY_CALORIES = "override_calories"
        private const val KEY_PROTEIN_PERCENT = "override_protein_percent"
        private const val KEY_FAT_PERCENT = "override_fat_percent"
        private const val KEY_CARBS_PERCENT = "override_carbs_percent"

        private const val KCAL_PER_GRAM_PROTEIN = 4
        private const val KCAL_PER_GRAM_CARBS = 4
        private const val KCAL_PER_GRAM_FAT = 9
    }
}
