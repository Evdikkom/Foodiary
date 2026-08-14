package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.usecase.CalculateNutritionTargetsUseCase
import com.example.foodiary.presentation.util.FoodiaryMotionPattern
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch

class GoalSettingsFragment : Fragment(R.layout.fragment_goal_settings) {

    companion object {
        private const val ARG_RETURN_DAY_START = "arg_return_day_start"

        fun newInstance(returnDayStart: Long = System.currentTimeMillis()): GoalSettingsFragment {
            return GoalSettingsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_RETURN_DAY_START, returnDayStart)
                }
            }
        }
    }

    private val returnDayStart: Long by lazy {
        arguments?.getLong(ARG_RETURN_DAY_START) ?: System.currentTimeMillis()
    }

    private val calculateNutritionTargets = CalculateNutritionTargetsUseCase()
    private var currentUser: User? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.buttonBack).setDebouncedClickListener {
            closeScreen()
        }
        view.findViewById<Button>(R.id.buttonSaveGoal).setDebouncedClickListener {
            saveGoal(view)
        }
        view.findViewById<RadioGroup>(R.id.groupGoal).setOnCheckedChangeListener { _, _ ->
            renderPreview(view)
        }

        loadUser(view)
    }

    private fun loadUser(root: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val database = AppDatabase.getInstance(requireContext())
            val userRepository = UserRepositoryImpl(
                userDao = database.userDao(),
                allergenDao = database.allergenDao(),
                userRestrictionDao = database.userRestrictionDao()
            )
            val user = userRepository.getCurrentUser()
            if (user == null) {
                Toast.makeText(requireContext(), "Сначала заполните параметры тела", Toast.LENGTH_SHORT).show()
                closeScreen()
                return@launch
            }
            currentUser = user
            root.findViewById<RadioGroup>(R.id.groupGoal).check(
                when (user.goal) {
                    UserGoal.WEIGHT_LOSS -> R.id.radioWeightLoss
                    UserGoal.MAINTAIN_WEIGHT -> R.id.radioMaintainWeight
                    UserGoal.WEIGHT_GAIN -> R.id.radioWeightGain
                    UserGoal.MUSCLE_GAIN_TRAINING -> R.id.radioMuscleGainTraining
                }
            )
            renderPreview(root)
        }
    }

    private fun selectedGoal(root: View): UserGoal {
        return when (root.findViewById<RadioGroup>(R.id.groupGoal).checkedRadioButtonId) {
            R.id.radioWeightLoss -> UserGoal.WEIGHT_LOSS
            R.id.radioWeightGain -> UserGoal.WEIGHT_GAIN
            R.id.radioMuscleGainTraining -> UserGoal.MUSCLE_GAIN_TRAINING
            else -> UserGoal.MAINTAIN_WEIGHT
        }
    }

    private fun renderPreview(root: View) {
        val user = currentUser ?: return
        val previewTargets = calculateNutritionTargets(user.copy(goal = selectedGoal(root)))
        root.findViewById<TextView>(R.id.textMaintenanceCaloriesValue).text =
            "${previewTargets.maintenanceCalories} ккал"
        root.findViewById<TextView>(R.id.textTargetCaloriesValue).text =
            "${previewTargets.targetCalories} ккал"
        root.findViewById<TextView>(R.id.textProteinValue).text = "${previewTargets.proteinGrams} г"
        root.findViewById<TextView>(R.id.textFatValue).text = "${previewTargets.fatGrams} г"
        root.findViewById<TextView>(R.id.textCarbsValue).text = "${previewTargets.carbsGrams} г"
        root.findViewById<TextView>(R.id.textGoalExplanation).text =
            buildGoalExplanation(selectedGoal(root), previewTargets)
    }

    private fun buildGoalExplanation(goal: UserGoal, targets: NutritionTargets): String {
        val base = when (goal) {
            UserGoal.WEIGHT_LOSS ->
                "Снижение массы использует дефицит относительно поддержания и помогает мягче снижать вес."
            UserGoal.MAINTAIN_WEIGHT ->
                "Поддержание оставляет калории на уровне расчётной потребности без дефицита и профицита."
            UserGoal.WEIGHT_GAIN ->
                "Обычный набор массы добавляет к поддержанию умеренный профицит и подходит для общего увеличения веса."
            UserGoal.MUSCLE_GAIN_TRAINING ->
                "Набор мышц использует более осторожный профицит и повышенный белок, чтобы лучше поддерживать рост безжировой массы."
        }
        return "$base Сейчас целевая калорийность: ${targets.targetCalories} ккал."
    }

    private fun saveGoal(root: View) {
        val user = currentUser ?: return
        val updatedUser = user.copy(goal = selectedGoal(root))

        viewLifecycleOwner.lifecycleScope.launch {
            val database = AppDatabase.getInstance(requireContext())
            val userRepository = UserRepositoryImpl(
                userDao = database.userDao(),
                allergenDao = database.allergenDao(),
                userRestrictionDao = database.userRestrictionDao()
            )
            userRepository.saveCurrentUser(updatedUser)
            Toast.makeText(requireContext(), "Цель питания обновлена", Toast.LENGTH_SHORT).show()
            closeScreen()
        }
    }

    private fun closeScreen() {
        if (parentFragmentManager.backStackEntryCount > 0) {
            popBackStackSafely()
            return
        }
        replaceFragmentSafely(
            DailyNutritionFragment.newInstance(returnDayStart),
            addToBackStack = false,
            motionPattern = FoodiaryMotionPattern.ROOT_FADE_THROUGH
        )
    }
}
