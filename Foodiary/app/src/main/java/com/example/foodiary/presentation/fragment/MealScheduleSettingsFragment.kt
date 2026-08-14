package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.preferences.MealSchedulePreferences
import com.example.foodiary.data.local.preferences.ReminderPreferences
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.notification.ReminderScheduler
import com.example.foodiary.presentation.util.EffectiveNutritionTargetsResolver
import com.example.foodiary.presentation.util.configurableMealTypes
import com.example.foodiary.presentation.util.displayName
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.primaryMealTypes
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch

class MealScheduleSettingsFragment : Fragment(R.layout.fragment_meal_schedule_settings) {

    companion object {
        fun newInstance(): MealScheduleSettingsFragment = MealScheduleSettingsFragment()
    }

    private data class RowViews(
        val root: View,
        val switch: SwitchCompat,
        val percentInput: EditText,
        val caloriesValue: TextView,
        val title: TextView,
        val note: TextView
    )

    private lateinit var mealPreferences: MealSchedulePreferences
    private lateinit var reminderPreferences: ReminderPreferences
    private lateinit var scheduler: ReminderScheduler
    private lateinit var resolver: EffectiveNutritionTargetsResolver

    private val rowViews = linkedMapOf<MealType, RowViews>()
    private var totalCalories = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mealPreferences = MealSchedulePreferences(requireContext())
        reminderPreferences = ReminderPreferences(requireContext())
        scheduler = ReminderScheduler(requireContext())
        resolver = EffectiveNutritionTargetsResolver(requireContext())

        view.findViewById<ImageView>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }
        view.findViewById<TextView>(R.id.buttonRestoreDefault).setDebouncedClickListener {
            bindSlots(mealPreferences.run { configurableMealTypes().map(::defaultSlot) })
        }
        view.findViewById<TextView>(R.id.buttonClear).setDebouncedClickListener {
            bindSlots(mealPreferences.clearEditableState())
        }
        view.findViewById<Button>(R.id.buttonSave).setDebouncedClickListener {
            saveSchedule(view)
        }

        loadTargetsAndRows(view)
    }

    private fun loadTargetsAndRows(root: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val database = AppDatabase.getInstance(requireContext())
            val userRepository = UserRepositoryImpl(
                userDao = database.userDao(),
                allergenDao = database.allergenDao(),
                userRestrictionDao = database.userRestrictionDao()
            )
            val user = userRepository.getCurrentUser()
            totalCalories = user?.let { resolver.resolve(it).targetCalories } ?: 0
            root.findViewById<TextView>(R.id.textCaloriesSummary).text =
                if (totalCalories > 0) "$totalCalories ккал" else "Нет базы"
            inflateRows(root)
            bindSlots(mealPreferences.getMealSlots())
        }
    }

    private fun inflateRows(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.layoutMealRows)
        container.removeAllViews()
        rowViews.clear()
        val fixedMealTypes = primaryMealTypes().toSet()

        configurableMealTypes().forEach { mealType ->
            val row = layoutInflater.inflate(R.layout.item_meal_schedule_row, container, false)
            val views = RowViews(
                root = row,
                switch = row.findViewById(R.id.switchMealEnabled),
                percentInput = row.findViewById(R.id.editPercent),
                caloriesValue = row.findViewById(R.id.textCaloriesValue),
                title = row.findViewById(R.id.textMealTitle),
                note = row.findViewById(R.id.textMealPercentLabel)
            )
            views.title.text = mealType.displayName()
            views.note.text = when {
                mealType in fixedMealTypes -> "основной"
                mealType == MealType.SNACK -> "по желанию"
                else -> "дополнительно"
            }
            if (mealType in fixedMealTypes) {
                views.switch.isChecked = true
                views.switch.isEnabled = false
                views.switch.alpha = 0.45f
            }
            views.switch.setOnCheckedChangeListener { _, _ -> updateRowState(mealType) }
            views.percentInput.doAfterTextChanged {
                updateCaloriesPreview(mealType)
                validateTotals(root)
            }
            rowViews[mealType] = views
            container.addView(row)
        }
    }

    private fun bindSlots(slots: List<MealSchedulePreferences.MealSlotSettings>) {
        val fixedMealTypes = primaryMealTypes().toSet()
        slots.forEach { slot ->
            val row = rowViews[slot.mealType] ?: return@forEach
            if (slot.mealType !in fixedMealTypes) {
                row.switch.isChecked = slot.enabled
            }
            row.percentInput.setText(slot.sharePercent.toString())
            updateRowState(slot.mealType)
        }
        validateTotals(requireView())
    }

    private fun updateRowState(mealType: MealType) {
        val row = rowViews[mealType] ?: return
        val enabled = if (mealType in primaryMealTypes()) {
            true
        } else {
            row.switch.isChecked
        }
        row.percentInput.isEnabled = enabled
        row.percentInput.alpha = if (enabled) 1f else 0.45f
        row.caloriesValue.alpha = if (enabled) 1f else 0.45f
        updateCaloriesPreview(mealType)
    }

    private fun updateCaloriesPreview(mealType: MealType) {
        val row = rowViews[mealType] ?: return
        val enabled = if (mealType in primaryMealTypes()) {
            true
        } else {
            row.switch.isChecked
        }
        val percent = row.percentInput.text?.toString()?.toIntOrNull() ?: 0
        val calories = if (enabled && totalCalories > 0) (totalCalories * percent / 100.0).toInt() else 0
        row.caloriesValue.text = "$calories ккал"
    }

    private fun validateTotals(root: View): Boolean {
        val error = root.findViewById<TextView>(R.id.textValidationError)
        val totalPercent = rowViews.entries.sumOf { (mealType, row) ->
            val enabled = if (mealType in primaryMealTypes()) {
                true
            } else {
                row.switch.isChecked
            }
            if (!enabled) 0 else row.percentInput.text?.toString()?.toIntOrNull() ?: 0
        }

        val hasInvalidEnabledSlot = rowViews.entries.any { (mealType, row) ->
            val enabled = if (mealType in primaryMealTypes()) {
                true
            } else {
                row.switch.isChecked
            }
            enabled && ((row.percentInput.text?.toString()?.toIntOrNull() ?: 0) <= 0)
        }

        val isValid = totalPercent == 100 && !hasInvalidEnabledSlot
        error.isVisible = !isValid
        if (!isValid) {
            error.text = when {
                hasInvalidEnabledSlot -> "У каждого включенного приёма пищи должна быть доля больше 0%."
                else -> "Сумма долей всех включенных приёмов пищи должна быть ровно 100%."
            }
        }
        return isValid
    }

    private fun saveSchedule(root: View) {
        if (!validateTotals(root)) return

        val slots = rowViews.map { (mealType, row) ->
            MealSchedulePreferences.MealSlotSettings(
                mealType = mealType,
                enabled = if (mealType in primaryMealTypes()) {
                    true
                } else {
                    row.switch.isChecked
                },
                sharePercent = row.percentInput.text?.toString()?.toIntOrNull() ?: 0
            )
        }
        mealPreferences.saveMealSlots(slots)
        reminderPreferences.ensureDefaultsForEnabledMeals(mealPreferences.getEnabledMealTypes())
        scheduler.rescheduleAll()
        popBackStackSafely()
    }
}
