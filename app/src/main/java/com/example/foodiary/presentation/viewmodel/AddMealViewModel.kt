package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.usecase.AddMealUseCase
import kotlinx.coroutines.launch
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class AddMealViewModel(
    private val foodRepository: FoodRepository,
    private val addMealUseCase: AddMealUseCase
) : ViewModel() {

    private val _selectedFoodId = MutableLiveData<String?>()
    val selectedFoodId: LiveData<String?> = _selectedFoodId

    private val _selectedFoodName = MutableLiveData<String>("Не выбран продукт")
    val selectedFoodName: LiveData<String> = _selectedFoodName

    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _saved = MutableLiveData(false)
    val saved: LiveData<Boolean> = _saved

    fun selectFood(foodId: String) {
        viewModelScope.launch {
            _error.value = null
            val food = foodRepository.getFoodById(foodId)
            _selectedFoodId.value = food.id
            _selectedFoodName.value = food.name
        }
    }

    fun saveMeal(
        quantityInGrams: Double,
        mealType: MealType,
        note: String
    ) {
        val foodId = _selectedFoodId.value
        if (foodId.isNullOrBlank()) {
            _error.value = "Сначала выберите продукт"
            return
        }
        if (quantityInGrams <= 0) {
            _error.value = "Количество должно быть больше 0"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null

            try {
                val meal = Meal(
                    foodId = foodId,
                    quantityInGrams = quantityInGrams,
                    mealType = mealType,
                    timestamp = System.currentTimeMillis(),
                    note = note
                )
                addMealUseCase(meal)
                _saved.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Ошибка сохранения"
            } finally {
                _isSaving.value = false
            }
        }
    }

    private val searchQuery = MutableStateFlow("")

    val foods = searchQuery
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else foodRepository.searchFoods(q)
        }
        .asLiveData()

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query.trim()
    }

}
