package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import kotlinx.coroutines.launch

class ImportFoodViewModel(
    private val importFoodByBarcodeUseCase: ImportFoodByBarcodeUseCase
) : ViewModel() {

    private val _isImporting = MutableLiveData(false)
    val isImporting: LiveData<Boolean> = _isImporting

    private val _importedFood = MutableLiveData<Food?>()
    val importedFood: LiveData<Food?> = _importedFood

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun import(barcodeRaw: String) {
        val barcode = barcodeRaw.trim()

        if (barcode.isBlank()) {
            _error.value = "Введите штрихкод"
            return
        }

        viewModelScope.launch {
            _isImporting.value = true
            _error.value = null
            try {
                val food = importFoodByBarcodeUseCase(barcode)
                _importedFood.value = food
            } catch (e: Exception) {
                _error.value = e.message ?: "Ошибка импорта"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun consumeImportedFood() {
        _importedFood.value = null
    }
}
