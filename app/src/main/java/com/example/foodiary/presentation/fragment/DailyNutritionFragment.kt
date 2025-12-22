package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.foodiary.databinding.FragmentDailyNutritionBinding
import com.example.foodiary.presentation.viewmodel.GetDailyNutritionViewModel
import java.util.Calendar

/**
 * DailyNutritionFragment — экран суточной аналитики питания.
 * Отображает калории, БЖУ и статистику приёмов пищи.
 */
class DailyNutritionFragment : Fragment() {

    private var _binding: FragmentDailyNutritionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GetDailyNutritionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyNutritionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()
        loadTodayNutrition()
    }

    private fun observeViewModel() {

        viewModel.dailyNutrition.observe(viewLifecycleOwner) { nutrition ->
            binding.textCalories.text =
                "Калории: ${nutrition.totalCalories.toInt()} ккал"

            binding.textProtein.text =
                "Белки: ${nutrition.totalProtein.toInt()} г"

            binding.textFat.text =
                "Жиры: ${nutrition.totalFat.toInt()} г"

            binding.textCarbs.text =
                "Углеводы: ${nutrition.totalCarbs.toInt()} г"

            binding.textMealsCount.text =
                "Приёмов пищи: ${nutrition.mealsCount}"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility =
                if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.textError.visibility =
                if (error != null) View.VISIBLE else View.GONE
            binding.textError.text = error
        }
    }

    /**
     * Загружает аналитику за текущий день.
     */
    private fun loadTodayNutrition() {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
