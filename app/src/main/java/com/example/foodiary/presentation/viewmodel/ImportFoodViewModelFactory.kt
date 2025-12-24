package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase

class ImportFoodViewModelFactory(
    private val useCase: ImportFoodByBarcodeUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImportFoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImportFoodViewModel(useCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
