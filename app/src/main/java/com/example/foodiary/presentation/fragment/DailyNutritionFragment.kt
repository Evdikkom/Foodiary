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
import com.example.foodiary.domain.usecase.DeleteMealUseCase
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import com.example.foodiary.domain.usecase.GetMealsForPeriodUseCase
import com.example.foodiary.presentation.viewmodel.GetDailyNutritionViewModel
import com.example.foodiary.presentation.viewmodel.GetDailyNutritionViewModelFactory

class DailyNutritionFragment : Fragment(R.layout.fragment_daily_nutrition) {

    private val viewModel: GetDailyNutritionViewModel by viewModels {
        provideFactory()
    }

    private fun getTodayBounds(): Pair<Long, Long> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val end = calendar.timeInMillis
        return start to end
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

        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerMealsToday)
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        val adapter = com.example.foodiary.presentation.adapter.MealsTodayAdapter { item ->
            val startEnd = getTodayBounds()
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Удалить приём пищи?")
                .setMessage("${item.foodName} • ${item.gramsText}")
                .setPositiveButton("Удалить") { _, _ ->
                    viewModel.deleteMeal(item.id, startEnd.first, startEnd.second)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
        recycler.adapter = adapter

        viewModel.mealsToday.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
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
        val (start, end) = getTodayBounds()
        viewModel.loadDailyNutrition(start, end)
        viewModel.loadMeals(start, end)
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
    private fun provideFactory(): GetDailyNutritionViewModelFactory {
        val database = AppDatabase.getInstance(requireContext())

        val foodRepository = FoodRepositoryImpl(foodDao = database.foodDao())
        val mealRepository = MealRepositoryImpl(mealDao = database.mealDao(), foodRepository = foodRepository)

        val getDailyNutrition = GetDailyNutritionUseCase(
            mealRepository = mealRepository,
            foodRepository = foodRepository
        )
        val getMealsForPeriod = GetMealsForPeriodUseCase(mealRepository)
        val deleteMeal = DeleteMealUseCase(mealRepository)

        return GetDailyNutritionViewModelFactory(
            getDailyNutritionUseCase = getDailyNutrition,
            getMealsForPeriodUseCase = getMealsForPeriod,
            deleteMealUseCase = deleteMeal,
            foodRepository = foodRepository
        )
    }

}
