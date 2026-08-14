package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import kotlinx.coroutines.launch
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.usecase.DeleteMealUseCase
import com.example.foodiary.domain.usecase.GetMealsForPeriodUseCase
import com.example.foodiary.presentation.model.MealItemUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GetDailyNutritionViewModel — ViewModel экрана аналитики питания.
 * Отвечает за загрузку и хранение суточных показателей питания.
 */
class GetDailyNutritionViewModel(
    private val getDailyNutritionUseCase: GetDailyNutritionUseCase,
    private val getMealsForPeriodUseCase: GetMealsForPeriodUseCase,
    private val deleteMealUseCase: DeleteMealUseCase,
    private val foodRepository: FoodRepository) : ViewModel() {

    private val _dailyNutrition = MutableLiveData<DailyNutrition>()
    val dailyNutrition: LiveData<DailyNutrition> = _dailyNutrition

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _mealsToday = MutableLiveData<List<MealItemUi>>(emptyList())
    val mealsToday: LiveData<List<MealItemUi>> = _mealsToday
    private var lastLoadedStartOfDay: Long? = null
    /**
     * Загружает суточную статистику питания.
     */
    fun loadDailyNutrition(startOfDay: Long, endOfDay: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            if (lastLoadedStartOfDay != startOfDay) {
                _dailyNutrition.value = emptyDailyNutrition()
                lastLoadedStartOfDay = startOfDay
            }

            try {
                val result = getDailyNutritionUseCase(
                    startOfDay = startOfDay,
                    endOfDay = endOfDay
                )
                _dailyNutrition.value = result
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMeals(startOfDay: Long, endOfDay: Long) {
        viewModelScope.launch {
            try {
                val meals = getMealsForPeriodUseCase(startOfDay, endOfDay)

                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val uiItems = meals.map { meal ->
                    val food = foodRepository.getFoodById(meal.foodId)

                    MealItemUi(
                        id = meal.id,
                        timeText = timeFormat.format(Date(meal.timestamp)),
                        mealType = meal.mealType,
                        foodName = food.name,
                        gramsText = "${meal.quantityInGrams.toInt()} г"
                    )
                }

                _mealsToday.value = uiItems
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteMeal(mealId: Long, startOfDay: Long, endOfDay: Long) {
        viewModelScope.launch {
            try {
                deleteMealUseCase(mealId)
                // после удаления обновляем и список, и суточную аналитику
                loadMeals(startOfDay, endOfDay)
                loadDailyNutrition(startOfDay, endOfDay)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun emptyDailyNutrition(): DailyNutrition {
        return DailyNutrition(
            totalCalories = 0.0,
            totalProtein = 0.0,
            totalFat = 0.0,
            totalCarbs = 0.0,
            mealsCount = 0,
            mealsByType = emptyMap(),
            caloriesByMealType = emptyMap()
        )
    }

}
