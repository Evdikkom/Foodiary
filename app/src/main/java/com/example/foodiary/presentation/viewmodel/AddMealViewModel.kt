package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import com.example.foodiary.domain.usecase.ImportFoodFromSearchItemUseCase
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class AddMealViewModel(
    private val foodRepository: FoodRepository,
    private val addMealUseCase: AddMealUseCase,
    private val importFoodByBarcodeUseCase: ImportFoodByBarcodeUseCase,
    private val importFoodFromSearchItemUseCase: ImportFoodFromSearchItemUseCase,
    private val searchFoodsByNameUseCase: SearchFoodsByNameUseCase
) : ViewModel() {

    companion object {
        private const val REMOTE_PAGE_SIZE = 20
        private const val TARGET_COMPLETE_PRODUCTS_PER_WAVE = 10
        private const val MAX_PAGES_PER_WAVE = 8
        private const val MAX_CONSECUTIVE_FAILED_PAGES = 2
    }

    private val _selectedFoodId = MutableLiveData<String?>(null)
    val selectedFoodId: LiveData<String?> = _selectedFoodId

    private val _selectedFoodName = MutableLiveData("Продукт ещё не выбран")
    val selectedFoodName: LiveData<String> = _selectedFoodName

    private val _selectedFoodImageUrl = MutableLiveData<String?>(null)
    val selectedFoodImageUrl: LiveData<String?> = _selectedFoodImageUrl

    private val _selectedFoodNutrition = MutableLiveData<String?>(null)
    val selectedFoodNutrition: LiveData<String?> = _selectedFoodNutrition

    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    private val _isImporting = MutableLiveData(false)
    val isImporting: LiveData<Boolean> = _isImporting

    private val _isRemoteSearching = MutableLiveData(false)
    val isRemoteSearching: LiveData<Boolean> = _isRemoteSearching

    private val _canLoadMoreRemoteFoods = MutableLiveData(false)
    val canLoadMoreRemoteFoods: LiveData<Boolean> = _canLoadMoreRemoteFoods

    private val _remoteSearchStatus = MutableLiveData<String?>(null)
    val remoteSearchStatus: LiveData<String?> = _remoteSearchStatus

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _saved = MutableLiveData(false)
    val saved: LiveData<Boolean> = _saved

    private val _remoteFoods = MutableLiveData<List<FoodSearchItem>>(emptyList())
    val remoteFoods: LiveData<List<FoodSearchItem>> = _remoteFoods

    private val searchQuery = MutableStateFlow("")

    private var currentRemoteQuery: String = ""
    private var nextRemotePageToLoad: Int? = null
    private var remoteSearchJob: Job? = null

    val foods = searchQuery
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            foodRepository.searchFoods(q)
        }
        .asLiveData()

    fun onSaveHandled() {
        _saved.value = false
    }

    private fun clearError() {
        _error.value = null
    }

    private fun cancelRemoteSearch() {
        remoteSearchJob?.cancel()
        remoteSearchJob = null
        _isRemoteSearching.value = false
    }

    private fun resetRemoteSearchState() {
        currentRemoteQuery = ""
        nextRemotePageToLoad = null
        _canLoadMoreRemoteFoods.value = false
        _remoteSearchStatus.value = null
    }

    private fun clearRemoteFoods() {
        _remoteFoods.value = emptyList()
        resetRemoteSearchState()
    }

    private fun buildNutritionText(
        calories: Double,
        protein: Double,
        fat: Double,
        carbs: Double
    ): String {
        return "Ккал: $calories • Б: $protein • Ж: $fat • У: $carbs"
    }

    fun onSearchQueryChanged(query: String) {
        clearError()

        val normalizedQuery = query.trim()
        searchQuery.value = normalizedQuery

        if (normalizedQuery.isBlank()) {
            cancelRemoteSearch()
            clearRemoteFoods()
        }
    }

    fun selectFood(foodId: String) {
        clearError()
        viewModelScope.launch {
            try {
                val food = foodRepository.getFoodById(foodId)
                _selectedFoodId.value = food.id
                _selectedFoodName.value = food.name
                _selectedFoodImageUrl.value = food.imageUrl?.takeIf { it.isNotBlank() }
                _selectedFoodNutrition.value = buildNutritionText(
                    calories = food.caloriesPer100g,
                    protein = food.proteinPer100g,
                    fat = food.fatPer100g,
                    carbs = food.carbsPer100g
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось выбрать продукт"
            }
        }
    }

    fun saveMeal(
        quantityInGrams: Double,
        mealType: MealType,
        note: String
    ) {
        clearError()

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
            try {
                val meal = Meal(
                    foodId = foodId,
                    quantityInGrams = quantityInGrams,
                    mealType = mealType,
                    timestamp = System.currentTimeMillis(),
                    note = note
                )
                addMealUseCase(meal)
                clearRemoteFoods()
                _saved.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Ошибка сохранения"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun importByBarcode(barcodeRaw: String) {
        clearError()

        val barcode = barcodeRaw.trim()
        if (barcode.isBlank()) {
            _error.value = "Введите штрихкод"
            return
        }

        viewModelScope.launch {
            _isImporting.value = true
            try {
                val importedFood = importFoodByBarcodeUseCase(barcode)
                _selectedFoodId.value = importedFood.id
                _selectedFoodName.value = importedFood.name
                _selectedFoodImageUrl.value = importedFood.imageUrl?.takeIf { it.isNotBlank() }
                _selectedFoodNutrition.value = buildNutritionText(
                    calories = importedFood.caloriesPer100g,
                    protein = importedFood.proteinPer100g,
                    fat = importedFood.fatPer100g,
                    carbs = importedFood.carbsPer100g
                )
                clearRemoteFoods()
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось добавить продукт"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun searchRemoteByName(queryRaw: String) {
        clearError()

        val query = queryRaw.trim()
        if (query.isBlank()) {
            cancelRemoteSearch()
            clearRemoteFoods()
            return
        }

        cancelRemoteSearch()
        _remoteFoods.value = emptyList()
        currentRemoteQuery = query
        nextRemotePageToLoad = 1
        _canLoadMoreRemoteFoods.value = false
        _remoteSearchStatus.value = "Ищу подходящие продукты..."

        remoteSearchJob = viewModelScope.launch {
            runRemoteWaveSearch(
                query = query,
                startPage = 1,
                append = false
            )
        }
    }

    fun loadMoreRemoteFoods() {
        if (_isRemoteSearching.value == true) return

        val query = currentRemoteQuery
        val startPage = nextRemotePageToLoad

        if (query.isBlank() || startPage == null) return

        clearError()
        _remoteSearchStatus.value = "Ищу ещё продукты..."

        remoteSearchJob = viewModelScope.launch {
            runRemoteWaveSearch(
                query = query,
                startPage = startPage,
                append = true
            )
        }
    }

    fun importFromRemoteItem(item: FoodSearchItem) {
        clearError()

        viewModelScope.launch {
            _isImporting.value = true
            try {
                val importedFood = importFoodFromSearchItemUseCase(item)
                _selectedFoodId.value = importedFood.id
                _selectedFoodName.value = importedFood.name
                _selectedFoodImageUrl.value = item.imageUrl?.takeIf { it.isNotBlank() }
                _selectedFoodNutrition.value = buildNutritionText(
                    calories = importedFood.caloriesPer100g,
                    protein = importedFood.proteinPer100g,
                    fat = importedFood.fatPer100g,
                    carbs = importedFood.carbsPer100g
                )
                clearRemoteFoods()
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось добавить продукт"
            } finally {
                _isImporting.value = false
            }
        }
    }

    private suspend fun runRemoteWaveSearch(
        query: String,
        startPage: Int,
        append: Boolean
    ) {
        _isRemoteSearching.postValue(true)

        val baseItems = if (append) _remoteFoods.value.orEmpty() else emptyList()
        val newlyCollectedItems = mutableListOf<FoodSearchItem>()

        var page = startPage
        var pagesChecked = 0
        var consecutiveFailures = 0
        var hasMorePages = true

        try {
            while (
                pagesChecked < MAX_PAGES_PER_WAVE &&
                hasMorePages &&
                newlyCollectedItems.size < TARGET_COMPLETE_PRODUCTS_PER_WAVE
            ) {
                try {
                    val pageResult = searchFoodsByNameUseCase(
                        query = query,
                        page = page,
                        pageSize = REMOTE_PAGE_SIZE
                    )

                    pagesChecked++
                    consecutiveFailures = 0

                    val existingBarcodes = (baseItems + newlyCollectedItems).map { it.barcode }.toSet()
                    val uniqueItems = pageResult.items.filterNot { it.barcode in existingBarcodes }

                    if (uniqueItems.isNotEmpty()) {
                        newlyCollectedItems.addAll(uniqueItems)

                        val currentItems = if (append) {
                            baseItems + newlyCollectedItems
                        } else {
                            newlyCollectedItems.toList()
                        }

                        _remoteFoods.postValue(currentItems)

                        if (newlyCollectedItems.size < TARGET_COMPLETE_PRODUCTS_PER_WAVE && pageResult.hasMore) {
                            _remoteSearchStatus.postValue(
                                "Пока нашлось ${currentItems.size} продукт(ов). Продолжаю поиск..."
                            )
                        }
                    }

                    hasMorePages = pageResult.hasMore
                    page = pageResult.nextPage ?: (page + 1)
                } catch (e: Exception) {
                    pagesChecked++
                    consecutiveFailures++

                    val currentItems = if (append) {
                        baseItems + newlyCollectedItems
                    } else {
                        newlyCollectedItems.toList()
                    }

                    if (currentItems.isNotEmpty()) {
                        _remoteFoods.postValue(currentItems)
                        _remoteSearchStatus.postValue(
                            "Пока нашлось ${currentItems.size} продукт(ов). Одна из страниц не ответила, ищу дальше..."
                        )
                        page += 1

                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILED_PAGES) {
                            break
                        }

                        continue
                    } else {
                        throw e
                    }
                }
            }

            val finalItems = if (append) {
                baseItems + newlyCollectedItems
            } else {
                newlyCollectedItems.toList()
            }

            _remoteFoods.postValue(finalItems)
            nextRemotePageToLoad = if (hasMorePages) page else null
            _canLoadMoreRemoteFoods.postValue(hasMorePages)

            when {
                finalItems.isEmpty() -> {
                    _remoteSearchStatus.postValue(null)
                    _error.postValue("Не найдено продуктов с полными данными")
                }

                newlyCollectedItems.size >= TARGET_COMPLETE_PRODUCTS_PER_WAVE -> {
                    _remoteSearchStatus.postValue(null)
                }

                hasMorePages -> {
                    _remoteSearchStatus.postValue(
                        "Показано ${finalItems.size} продукт(ов). Можно нажать «Показать ещё»."
                    )
                }

                else -> {
                    _remoteSearchStatus.postValue(
                        "Показано ${finalItems.size} продукт(ов). Больше подходящих результатов не найдено."
                    )
                }
            }
        } catch (e: Exception) {
            _remoteSearchStatus.postValue(null)
            _error.postValue(e.message ?: "Ошибка поиска в базе продуктов")
        } finally {
            _isRemoteSearching.postValue(false)
        }
    }
}