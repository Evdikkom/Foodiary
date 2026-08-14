package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodRecommendation
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.GetPersonalizedFoodRecommendationsUseCase
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import com.example.foodiary.domain.usecase.ImportFoodFromSearchItemUseCase
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val searchFoodsByNameUseCase: SearchFoodsByNameUseCase,
    private val getPersonalizedFoodRecommendationsUseCase: GetPersonalizedFoodRecommendationsUseCase
) : ViewModel() {

    companion object {
        private const val REMOTE_PAGE_SIZE = 4
        private const val TARGET_COMPLETE_PRODUCTS_PER_WAVE = 12
        private const val MAX_INITIAL_PAGES_PER_WAVE = 10
        private const val MAX_APPEND_PAGES_PER_WAVE = 4
        private const val MAX_CONSECUTIVE_FAILED_PAGES = 3
        private const val RECOMMENDATIONS_LIMIT = 8
        private const val REMOTE_SEARCH_MIN_QUERY_LENGTH = 3
        private const val REMOTE_SEARCH_DEBOUNCE_MS = 850L
    }

    private val _selectedFoodId = MutableLiveData<String?>(null)
    val selectedFoodId: LiveData<String?> = _selectedFoodId

    private val _silentlyImportedFoodId = MutableLiveData<String?>(null)
    val silentlyImportedFoodId: LiveData<String?> = _silentlyImportedFoodId

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
    private var remoteSearchDebounceJob: Job? = null

    val foods = searchQuery
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            foodRepository.searchFoods(q)
        }
        .asLiveData()

    private val _recommendedFoods = MutableLiveData<List<FoodRecommendation>>(emptyList())
    val recommendedFoods: LiveData<List<FoodRecommendation>> = _recommendedFoods

    fun onSaveHandled() {
        _saved.value = false
    }

    fun onSilentlyImportedFoodHandled() {
        _silentlyImportedFoodId.value = null
    }

    fun loadRecommendations(mealType: MealType) {
        viewModelScope.launch {
            try {
                _recommendedFoods.value = getPersonalizedFoodRecommendationsUseCase(
                    mealType = mealType,
                    limit = RECOMMENDATIONS_LIMIT
                )
            } catch (_: Exception) {
                _recommendedFoods.value = emptyList()
            }
        }
    }

    private fun clearError() {
        _error.value = null
    }

    private fun cancelRemoteSearch() {
        remoteSearchDebounceJob?.cancel()
        remoteSearchDebounceJob = null
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

    private fun clearRemoteFoods(resetState: Boolean = true) {
        _remoteFoods.value = emptyList()
        if (resetState) {
            resetRemoteSearchState()
        } else {
            _canLoadMoreRemoteFoods.value = false
            _remoteSearchStatus.value = null
        }
    }

    private fun buildNutritionText(
        calories: Double,
        protein: Double,
        fat: Double,
        carbs: Double
    ): String {
        return "Ккал: $calories, Б: $protein, Ж: $fat, У: $carbs"
    }

    fun onSearchQueryChanged(query: String) {
        clearError()

        val normalizedQuery = query.trim()
        searchQuery.value = normalizedQuery

        cancelRemoteSearch()

        if (normalizedQuery.isBlank()) {
            clearRemoteFoods()
            return
        }

        if (normalizedQuery.length < REMOTE_SEARCH_MIN_QUERY_LENGTH) {
            clearRemoteFoods()
            return
        }

        remoteSearchDebounceJob = viewModelScope.launch {
            delay(REMOTE_SEARCH_DEBOUNCE_MS)

            _remoteFoods.value = emptyList()
            currentRemoteQuery = normalizedQuery
            nextRemotePageToLoad = 1
            _canLoadMoreRemoteFoods.value = false
            _remoteSearchStatus.value = "Ищу продукты в общей базе..."

            remoteSearchJob = launch {
                runRemoteWaveSearch(
                    query = normalizedQuery,
                    startPage = 1,
                    append = false
                )
            }
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

    fun importFromRemoteItem(
        item: FoodSearchItem,
        openAfterImport: Boolean = true
    ) {
        clearError()

        viewModelScope.launch {
            _isImporting.value = true
            try {
                val importedFood = importFoodFromSearchItemUseCase(item)

                if (openAfterImport) {
                    _selectedFoodId.value = importedFood.id
                    _selectedFoodName.value = importedFood.name
                    _selectedFoodImageUrl.value = item.imageUrl?.takeIf { it.isNotBlank() }
                    _selectedFoodNutrition.value = buildNutritionText(
                        calories = importedFood.caloriesPer100g,
                        protein = importedFood.proteinPer100g,
                        fat = importedFood.fatPer100g,
                        carbs = importedFood.carbsPer100g
                    )
                    clearRemoteFoods(resetState = false)
                } else {
                    _silentlyImportedFoodId.value = importedFood.id
                    _remoteSearchStatus.value = "Продукт сохранён в локальную базу"
                }
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
        val maxPagesPerWave = if (append) {
            MAX_APPEND_PAGES_PER_WAVE
        } else {
            MAX_INITIAL_PAGES_PER_WAVE
        }

        var page = startPage
        var pagesChecked = 0
        var consecutiveFailures = 0
        var hasMorePages = true

        try {
            while (
                pagesChecked < maxPagesPerWave &&
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

                    val existingBarcodes = (baseItems + newlyCollectedItems)
                        .map { it.barcode }
                        .toSet()

                    val uniqueItems = pageResult.items.filterNot { it.barcode in existingBarcodes }

                    if (uniqueItems.isNotEmpty()) {
                        newlyCollectedItems.addAll(uniqueItems)

                        val currentItems = if (append) {
                            baseItems + newlyCollectedItems
                        } else {
                            newlyCollectedItems.toList()
                        }

                        _remoteFoods.postValue(currentItems)

                        if (
                            newlyCollectedItems.size < TARGET_COMPLETE_PRODUCTS_PER_WAVE &&
                            pageResult.hasMore
                        ) {
                            _remoteSearchStatus.postValue(
                                "Пока нашлось ${currentItems.size} продукт(ов). Продолжаю поиск..."
                            )
                        }
                    } else if (!append && pageResult.hasMore) {
                        _remoteSearchStatus.postValue(
                            "Первые страницы пока без полного КБЖУ. Проверяю следующие результаты..."
                        )
                    }

                    hasMorePages = pageResult.hasMore
                    page = pageResult.nextPage ?: (page + 1)

                    if (!append && newlyCollectedItems.isNotEmpty()) {
                        break
                    }
                } catch (e: Exception) {
                    pagesChecked++

                    val message = e.message.orEmpty()
                    val isTimeoutLikeError =
                        message.contains("слишком долго", ignoreCase = true) ||
                            message.contains("timeout", ignoreCase = true) ||
                            message.contains("timed out", ignoreCase = true) ||
                            message.contains("пропускаю эту страницу", ignoreCase = true)

                    if (!isTimeoutLikeError) {
                        throw e
                    }

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
                    } else {
                        _remoteSearchStatus.postValue(
                            "Страница не ответила вовремя. Пробую следующую..."
                        )
                    }

                    page += 1
                    hasMorePages = true

                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILED_PAGES) {
                        break
                    }

                    continue
                }
            }

            val finalItems = if (append) {
                baseItems + newlyCollectedItems
            } else {
                newlyCollectedItems.toList()
            }

            _remoteFoods.postValue(finalItems)
            val canContinueManually = finalItems.isNotEmpty() && hasMorePages
            nextRemotePageToLoad = if (canContinueManually) page else null
            _canLoadMoreRemoteFoods.postValue(canContinueManually)

            when {
                finalItems.isEmpty() && consecutiveFailures > 0 -> {
                    _remoteSearchStatus.postValue(
                        "Несколько страниц не ответили вовремя. Попробуй повторить поиск."
                    )
                }

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
            _error.postValue(buildRemoteSearchErrorMessage(e))
        } finally {
            _isRemoteSearching.postValue(false)
        }
    }

    private fun buildRemoteSearchErrorMessage(error: Throwable): String {
        val message = error.message.orEmpty()

        return when {
            message.contains("EPERM", ignoreCase = true) ||
                message.contains("Operation not permitted", ignoreCase = true) ||
                message.contains("Binding socket", ignoreCase = true) ->
                "\u041f\u043e\u0445\u043e\u0436\u0435, VPN \u043e\u0433\u0440\u0430\u043d\u0438\u0447\u0438\u043b \u0441\u0435\u0442\u0435\u0432\u043e\u0439 \u0437\u0430\u043f\u0440\u043e\u0441. \u041f\u043e\u043f\u0440\u043e\u0431\u0443\u0439 \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u044c \u043f\u043e\u0438\u0441\u043a \u0438\u043b\u0438 \u0440\u0430\u0437\u0440\u0435\u0448\u0438 Foodiary \u0440\u0430\u0431\u043e\u0442\u0443 \u0447\u0435\u0440\u0435\u0437 VPN."

            message.contains("HTTP 429", ignoreCase = true) ||
                message.contains("HTTP 503", ignoreCase = true) ||
                message.contains("Service Unavailable", ignoreCase = true) ->
                "\u0411\u0430\u0437\u0430 Open Food Facts \u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e \u043d\u0435 \u043e\u0442\u0432\u0435\u0442\u0438\u043b\u0430. \u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0435 \u043f\u0440\u043e\u0434\u0443\u043a\u0442\u044b Foodiary \u043e\u0441\u0442\u0430\u043b\u0438\u0441\u044c \u0432 \u0441\u043f\u0438\u0441\u043a\u0435, \u0432\u043d\u0435\u0448\u043d\u0438\u0439 \u043f\u043e\u0438\u0441\u043a \u043c\u043e\u0436\u043d\u043e \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u044c \u0447\u0443\u0442\u044c \u043f\u043e\u0437\u0436\u0435."

            message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ->
                "\u0411\u0430\u0437\u0430 \u043f\u0440\u043e\u0434\u0443\u043a\u0442\u043e\u0432 \u043e\u0442\u0432\u0435\u0447\u0430\u0435\u0442 \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u0434\u043e\u043b\u0433\u043e. \u041f\u043e\u043f\u0440\u043e\u0431\u0443\u0439 \u0435\u0449\u0451 \u0440\u0430\u0437."

            message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("UnknownHost", ignoreCase = true) ->
                "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043d\u0430\u0439\u0442\u0438 \u0431\u0430\u0437\u0443 \u043f\u0440\u043e\u0434\u0443\u043a\u0442\u043e\u0432 \u0432 \u0441\u0435\u0442\u0438. \u041f\u0440\u043e\u0432\u0435\u0440\u044c \u0438\u043d\u0442\u0435\u0440\u043d\u0435\u0442 \u0438\u043b\u0438 VPN."

            else ->
                message.takeIf { it.isNotBlank() }
                    ?: "\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u043e\u0438\u0441\u043a\u0430 \u0432 \u0431\u0430\u0437\u0435 \u043f\u0440\u043e\u0434\u0443\u043a\u0442\u043e\u0432"
        }
    }
}
