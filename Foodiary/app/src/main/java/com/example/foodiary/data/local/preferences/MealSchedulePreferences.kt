package com.example.foodiary.data.local.preferences

import android.content.Context
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.util.configurableMealTypes
import com.example.foodiary.presentation.util.primaryMealTypes
import kotlin.math.roundToInt

class MealSchedulePreferences(context: Context) {

    data class MealSlotSettings(
        val mealType: MealType,
        val enabled: Boolean,
        val sharePercent: Int
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMealSlots(): List<MealSlotSettings> {
        return configurableMealTypes().map { mealType ->
            val defaults = defaultSlot(mealType)
            MealSlotSettings(
                mealType = mealType,
                enabled = prefs.getBoolean(enabledKey(mealType), defaults.enabled),
                sharePercent = prefs.getInt(percentKey(mealType), defaults.sharePercent)
            )
        }
    }

    fun getEnabledMealTypes(): List<MealType> {
        return getMealSlots().filter { it.enabled }.map { it.mealType }
    }

    fun saveMealSlots(slots: List<MealSlotSettings>) {
        prefs.edit().apply {
            configurableMealTypes().forEach { type ->
                remove(enabledKey(type))
                remove(percentKey(type))
            }
            slots.forEach { slot ->
                putBoolean(enabledKey(slot.mealType), slot.enabled)
                putInt(percentKey(slot.mealType), slot.sharePercent)
            }
        }.apply()
    }

    fun resetToDefaults() {
        saveMealSlots(configurableMealTypes().map(::defaultSlot))
    }

    fun clearEditableState(): List<MealSlotSettings> {
        return configurableMealTypes().map { mealType ->
            defaultSlot(mealType).copy(
                enabled = mealType in primaryMealTypes(),
                sharePercent = 0
            )
        }
    }

    fun buildCalorieTargets(totalCalories: Int): Map<MealType, Int> {
        val enabledSlots = getMealSlots().filter { it.enabled && it.sharePercent > 0 }
        if (enabledSlots.isEmpty()) return emptyMap()

        val preliminary = enabledSlots.map { slot ->
            slot.mealType to (totalCalories * slot.sharePercent / 100.0)
        }
        val rounded = preliminary.associate { it.first to it.second.roundToInt() }.toMutableMap()
        val diff = totalCalories - rounded.values.sum()
        if (diff != 0) {
            val largest = enabledSlots.maxByOrNull { it.sharePercent }?.mealType
            if (largest != null) {
                rounded[largest] = (rounded[largest] ?: 0) + diff
            }
        }
        return rounded
    }

    fun defaultSlot(mealType: MealType): MealSlotSettings = when (mealType) {
        MealType.BREAKFAST -> MealSlotSettings(mealType, enabled = true, sharePercent = 30)
        MealType.LUNCH -> MealSlotSettings(mealType, enabled = true, sharePercent = 40)
        MealType.DINNER -> MealSlotSettings(mealType, enabled = true, sharePercent = 20)
        MealType.SNACK -> MealSlotSettings(mealType, enabled = true, sharePercent = 10)
        MealType.AFTERNOON_SNACK -> MealSlotSettings(mealType, enabled = false, sharePercent = 0)
        MealType.LATE_DINNER -> MealSlotSettings(mealType, enabled = false, sharePercent = 0)
    }

    private fun enabledKey(mealType: MealType): String = "meal_enabled_${mealType.name}"
    private fun percentKey(mealType: MealType): String = "meal_percent_${mealType.name}"

    companion object {
        private const val PREFS_NAME = "foodiary_meal_schedule"
    }
}
