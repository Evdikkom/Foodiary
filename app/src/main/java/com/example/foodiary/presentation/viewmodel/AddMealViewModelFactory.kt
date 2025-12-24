package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase

class AddMealViewModelFactory(
    private val foodRepository: FoodRepository,
    private val addMealUseCase: AddMealUseCase,
    private val importFoodByBarcodeUseCase: ImportFoodByBarcodeUseCase,
    private val searchFoodsByNameUseCase: SearchFoodsByNameUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddMealViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddMealViewModel(
                foodRepository = foodRepository,
                addMealUseCase = addMealUseCase,
                importFoodByBarcodeUseCase = importFoodByBarcodeUseCase,
                searchFoodsByNameUseCase = searchFoodsByNameUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
