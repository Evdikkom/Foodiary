package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.*
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AddMealViewModel(
    private val foodRepository: FoodRepository,
    private val addMealUseCase: AddMealUseCase,
    private val importFoodByBarcodeUseCase: ImportFoodByBarcodeUseCase,
    private val searchFoodsByNameUseCase: SearchFoodsByNameUseCase
) : ViewModel() {

    private val _selectedFoodId = MutableLiveData<String?>()
    val selectedFoodId: LiveData<String?> = _selectedFoodId

    private val _selectedFoodName = MutableLiveData("Не выбран продукт")
    val selectedFoodName: LiveData<String> = _selectedFoodName

    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    private val _isImporting = MutableLiveData(false)
    val isImporting: LiveData<Boolean> = _isImporting

    private val _isRemoteSearching = MutableLiveData(false)
    val isRemoteSearching: LiveData<Boolean> = _isRemoteSearching

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _saved = MutableLiveData(false)
    val saved: LiveData<Boolean> = _saved

    private val _remoteFoods = MutableLiveData<List<FoodSearchItem>>(emptyList())
    val remoteFoods: LiveData<List<FoodSearchItem>> = _remoteFoods

    // ---------------- Локальный поиск (Room) ----------------
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

    // ---------------- Выбор продукта (локально) ----------------
    fun selectFood(foodId: String) {
        viewModelScope.launch {
            _error.value = null
            val food = foodRepository.getFoodById(foodId)
            _selectedFoodId.value = food.id
            _selectedFoodName.value = food.name
        }
    }

    // ---------------- Сохранение Meal (приём пищи) ----------------
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

    // ---------------- Импорт по штрихкоду (barcode) ----------------
    fun importByBarcode(barcodeRaw: String) {
        val barcode = barcodeRaw.trim()
        if (barcode.isBlank()) {
            _error.value = "Введите штрихкод"
            return
        }

        viewModelScope.launch {
            _isImporting.value = true
            _error.value = null
            try {
                val importedFood = importFoodByBarcodeUseCase(barcode)
                selectFood(importedFood.id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Ошибка импорта продукта"
            } finally {
                _isImporting.value = false
            }
        }
    }

    // ---------------- Поиск в OpenFoodFacts по словам ----------------
    fun searchRemoteByName(queryRaw: String) {
        val query = queryRaw.trim()
        if (query.isBlank()) {
            _remoteFoods.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isRemoteSearching.value = true
            _error.value = null
            try {
                val results = searchFoodsByNameUseCase(query = query, page = 1, pageSize = 20)
                _remoteFoods.value = results
            } catch (e: Exception) {
                _error.value = e.message ?: "Ошибка поиска в OpenFoodFacts"
            } finally {
                _isRemoteSearching.value = false
            }
        }
    }

    // ---------------- Импорт выбранного remote-результата ----------------
    fun importFromRemoteItem(item: FoodSearchItem) {
        viewModelScope.launch {
            _isImporting.value = true
            _error.value = null
            try {
                val importedFood = importFoodByBarcodeUseCase(item.barcode)
                // после импорта — выбираем продукт для текущего приёма пищи
                selectFood(importedFood.id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Ошибка импорта продукта"
            } finally {
                _isImporting.value = false
            }
        }
    }
}
