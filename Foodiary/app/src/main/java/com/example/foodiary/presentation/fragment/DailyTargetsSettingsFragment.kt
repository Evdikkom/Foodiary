package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.preferences.NutritionTargetsPreferences
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.User
import com.example.foodiary.presentation.util.EffectiveNutritionTargetsResolver
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DailyTargetsSettingsFragment : Fragment(R.layout.fragment_daily_targets_settings) {

    companion object {
        fun newInstance(): DailyTargetsSettingsFragment = DailyTargetsSettingsFragment()
    }

    private lateinit var preferences: NutritionTargetsPreferences
    private lateinit var resolver: EffectiveNutritionTargetsResolver

    private var currentUser: User? = null
    private var autoTargets: NutritionTargets? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = NutritionTargetsPreferences(requireContext())
        resolver = EffectiveNutritionTargetsResolver(requireContext())

        view.findViewById<ImageView>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }

        val recalculate = { renderDerivedValues(view) }
        listOf(
            view.findViewById<EditText>(R.id.editCalories),
            view.findViewById<EditText>(R.id.editProteinPercent),
            view.findViewById<EditText>(R.id.editFatPercent),
            view.findViewById<EditText>(R.id.editCarbsPercent)
        ).forEach { field ->
            field.doAfterTextChanged { recalculate() }
        }

        view.findViewById<TextView>(R.id.buttonRestoreDefault).setDebouncedClickListener {
            autoTargets?.let { bindDefaultValues(view, it) }
        }
        view.findViewById<TextView>(R.id.buttonClearOverride).setDebouncedClickListener {
            preferences.clearOverride()
            autoTargets?.let { bindDefaultValues(view, it) }
            Toast.makeText(requireContext(), "Возврат к автоматическим нормам", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.buttonSave).setDebouncedClickListener {
            saveOverride(view)
        }

        loadProfile(view)
    }

    private fun loadProfile(root: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val database = AppDatabase.getInstance(requireContext())
            val userRepository = UserRepositoryImpl(
                userDao = database.userDao(),
                allergenDao = database.allergenDao(),
                userRestrictionDao = database.userRestrictionDao()
            )
            currentUser = userRepository.getCurrentUser()
            val user = currentUser
            if (user == null) {
                root.findViewById<TextView>(R.id.textBaseSummary).text =
                    "Сначала заполните параметры тела и цель, чтобы Foodiary смог построить базовую норму."
                root.findViewById<Button>(R.id.buttonSave).isEnabled = false
                return@launch
            }

            autoTargets = resolver.calculateAutoTargets(user)
            val override = preferences.getOverride()
            if (override == null) {
                bindDefaultValues(root, autoTargets!!)
            } else {
                root.findViewById<EditText>(R.id.editCalories).setText(override.calories.toString())
                root.findViewById<EditText>(R.id.editProteinPercent).setText(override.proteinPercent.toString())
                root.findViewById<EditText>(R.id.editFatPercent).setText(override.fatPercent.toString())
                root.findViewById<EditText>(R.id.editCarbsPercent).setText(override.carbsPercent.toString())
                renderDerivedValues(root)
            }
        }
    }

    private fun bindDefaultValues(root: View, targets: NutritionTargets) {
        val percents = calculatePercents(targets)
        root.findViewById<EditText>(R.id.editCalories).setText(targets.targetCalories.toString())
        root.findViewById<EditText>(R.id.editProteinPercent).setText(percents.first.toString())
        root.findViewById<EditText>(R.id.editFatPercent).setText(percents.second.toString())
        root.findViewById<EditText>(R.id.editCarbsPercent).setText(percents.third.toString())
        renderDerivedValues(root)
    }

    private fun renderDerivedValues(root: View) {
        val calories = root.findViewById<EditText>(R.id.editCalories).text?.toString()?.toIntOrNull() ?: 0
        val proteinPercent = root.findViewById<EditText>(R.id.editProteinPercent).text?.toString()?.toIntOrNull() ?: 0
        val fatPercent = root.findViewById<EditText>(R.id.editFatPercent).text?.toString()?.toIntOrNull() ?: 0
        val carbsPercent = root.findViewById<EditText>(R.id.editCarbsPercent).text?.toString()?.toIntOrNull() ?: 0
        val base = autoTargets

        if (base != null) {
            val effective = preferences.getOverride()
            val usingManual = effective != null
            root.findViewById<TextView>(R.id.textBaseSummary).text =
                if (usingManual) {
                    "Автоматическая база: ${base.targetCalories} ккал. Сейчас поверх неё используется ручная настройка."
                } else {
                    "Автоматическая база: ${base.targetCalories} ккал, ${base.proteinGrams} г белка, ${base.fatGrams} г жиров и ${base.carbsGrams} г углеводов."
                }
        }

        root.findViewById<TextView>(R.id.textProteinGrams).text =
            "${((calories * (proteinPercent / 100.0)) / 4.0).roundToInt()} г"
        root.findViewById<TextView>(R.id.textFatGrams).text =
            "${((calories * (fatPercent / 100.0)) / 9.0).roundToInt()} г"
        root.findViewById<TextView>(R.id.textCarbsGrams).text =
            "${((calories * (carbsPercent / 100.0)) / 4.0).roundToInt()} г"

        val error = root.findViewById<TextView>(R.id.textValidationError)
        val totalPercent = proteinPercent + fatPercent + carbsPercent
        error.isVisible = totalPercent != 100 && calories > 0
        if (error.isVisible) {
            error.text = "Сумма белков, жиров и углеводов должна быть ровно 100%."
        }
    }

    private fun saveOverride(root: View) {
        val calories = root.findViewById<EditText>(R.id.editCalories).text?.toString()?.toIntOrNull() ?: 0
        val proteinPercent = root.findViewById<EditText>(R.id.editProteinPercent).text?.toString()?.toIntOrNull() ?: 0
        val fatPercent = root.findViewById<EditText>(R.id.editFatPercent).text?.toString()?.toIntOrNull() ?: 0
        val carbsPercent = root.findViewById<EditText>(R.id.editCarbsPercent).text?.toString()?.toIntOrNull() ?: 0

        if (calories <= 0) {
            Toast.makeText(requireContext(), "Укажите корректную калорийность", Toast.LENGTH_SHORT).show()
            return
        }
        if (proteinPercent + fatPercent + carbsPercent != 100) {
            root.findViewById<TextView>(R.id.textValidationError).apply {
                isVisible = true
                text = "Сумма макронутриентов должна быть равна 100%."
            }
            return
        }

        preferences.saveOverride(
            NutritionTargetsPreferences.NutritionOverride(
                calories = calories,
                proteinPercent = proteinPercent,
                fatPercent = fatPercent,
                carbsPercent = carbsPercent
            )
        )
        Toast.makeText(requireContext(), "Нормы сохранены", Toast.LENGTH_SHORT).show()
        popBackStackSafely()
    }

    private fun calculatePercents(targets: NutritionTargets): Triple<Int, Int, Int> {
        if (targets.targetCalories <= 0) return Triple(20, 30, 50)
        val proteinPercent = ((targets.proteinGrams * 4.0 / targets.targetCalories) * 100).roundToInt()
        val fatPercent = ((targets.fatGrams * 9.0 / targets.targetCalories) * 100).roundToInt()
        val carbsPercent = (100 - proteinPercent - fatPercent).coerceAtLeast(0)
        return Triple(proteinPercent, fatPercent, carbsPercent)
    }
}
