package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import com.example.foodiary.presentation.viewmodel.GetDailyNutritionViewModel
import com.example.foodiary.presentation.viewmodel.GetDailyNutritionViewModelFactory
import java.util.Calendar
import com.example.foodiary.presentation.fragment.AddMealFragment

class DailyNutritionFragment : Fragment(R.layout.fragment_daily_nutrition) {

    private val viewModel: GetDailyNutritionViewModel by viewModels {
        GetDailyNutritionViewModelFactory(provideUseCase())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel(view)
        loadToday()

        val btn = view.findViewById<Button>(R.id.buttonOpenAddMeal)
        btn.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AddMealFragment())
                .addToBackStack(null)
                .commit()
        }

    }

    override fun onResume() {
        super.onResume()
        loadToday()
    }
    private fun observeViewModel(root: View) {
        val progress = root.findViewById<View>(R.id.progressBar)
        val error = root.findViewById<android.widget.TextView>(R.id.textError)

        val calories = root.findViewById<android.widget.TextView>(R.id.textCalories)
        val protein = root.findViewById<android.widget.TextView>(R.id.textProtein)
        val fat = root.findViewById<android.widget.TextView>(R.id.textFat)
        val carbs = root.findViewById<android.widget.TextView>(R.id.textCarbs)
        val mealsCount = root.findViewById<android.widget.TextView>(R.id.textMealsCount)

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { message ->
            if (message.isNullOrBlank()) {
                error.visibility = View.GONE
            } else {
                error.visibility = View.VISIBLE
                error.text = message
            }
        }

        viewModel.dailyNutrition.observe(viewLifecycleOwner) { dn ->
            calories.text = "Калории: ${dn.totalCalories.toInt()} ккал"
            protein.text = "Белки: ${dn.totalProtein.toInt()} г"
            fat.text = "Жиры: ${dn.totalFat.toInt()} г"
            carbs.text = "Углеводы: ${dn.totalCarbs.toInt()} г"
            mealsCount.text = "Приёмов пищи: ${dn.mealsCount}"
        }
    }

    private fun loadToday() {
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        viewModel.loadDailyNutrition(startOfDay, endOfDay)
    }

    private fun provideUseCase(): GetDailyNutritionUseCase {
        val database = AppDatabase.getInstance(requireContext())

        val foodRepository = FoodRepositoryImpl(
            foodDao = database.foodDao()
        )

        val mealRepository = MealRepositoryImpl(
            mealDao = database.mealDao(),
            foodRepository = foodRepository
        )

        return GetDailyNutritionUseCase(
            mealRepository = mealRepository,
            foodRepository = foodRepository
        )

    }
}
