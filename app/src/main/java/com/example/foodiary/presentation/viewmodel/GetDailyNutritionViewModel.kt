package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import kotlinx.coroutines.launch

/**
 * GetDailyNutritionViewModel — ViewModel экрана аналитики питания.
 * Отвечает за загрузку и хранение суточных показателей питания.
 */
class GetDailyNutritionViewModel(
    private val getDailyNutritionUseCase: GetDailyNutritionUseCase
) : ViewModel() {

    private val _dailyNutrition = MutableLiveData<DailyNutrition>()
    val dailyNutrition: LiveData<DailyNutrition> = _dailyNutrition

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Загружает суточную статистику питания.
     */
    fun loadDailyNutrition(startOfDay: Long, endOfDay: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

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
}
