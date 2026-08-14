package com.example.foodiary.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.ProteinGoalBasis
import com.example.foodiary.domain.model.RemoteFoodSearchPage
import com.example.foodiary.domain.repository.FoodImportRepository
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import com.example.foodiary.domain.usecase.GetPersonalizedFoodRecommendationsUseCase
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import com.example.foodiary.domain.usecase.ImportFoodFromSearchItemUseCase
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase
import com.example.foodiary.testing.FakeAllergenRepository
import com.example.foodiary.testing.FakeFavoriteFoodsRepository
import com.example.foodiary.testing.FakeFoodRepository
import com.example.foodiary.testing.FakeMealRepository
import com.example.foodiary.testing.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class AddMealViewModelRemoteSearchTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial remote search keeps paging until first complete results`() =
        runTest(mainDispatcherRule.dispatcher) {
        val completeItem = FoodSearchItem(
            barcode = "remote-3",
            name = "Сырок",
            brand = "Test",
            imageUrl = null,
            caloriesPer100g = 320.0,
            proteinPer100g = 8.0,
            fatPer100g = 18.0,
            carbsPer100g = 31.0
        )
        val importRepository = PagingFoodImportRepository(
            pages = mapOf(
                1 to RemoteFoodSearchPage(items = emptyList(), nextPage = 2, hasMore = true),
                2 to RemoteFoodSearchPage(items = emptyList(), nextPage = 3, hasMore = true),
                3 to RemoteFoodSearchPage(items = listOf(completeItem), nextPage = 4, hasMore = true)
            )
        )
        val viewModel = createViewModel(importRepository)

        viewModel.onSearchQueryChanged("сырок")

        advanceTimeBy(900)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), importRepository.requestedPages)
        assertEquals(listOf(completeItem), viewModel.remoteFoods.value)
        assertEquals(true, viewModel.canLoadMoreRemoteFoods.value)
        assertEquals(false, viewModel.isRemoteSearching.value)
        }

    @Test
    fun `initial remote search skips one temporary page failure before showing empty state`() =
        runTest(mainDispatcherRule.dispatcher) {
        val completeItem = FoodSearchItem(
            barcode = "remote-2",
            name = "Рис",
            brand = "Test",
            imageUrl = null,
            caloriesPer100g = 360.0,
            proteinPer100g = 7.0,
            fatPer100g = 1.0,
            carbsPer100g = 79.0
        )
        val importRepository = PagingFoodImportRepository(
            pages = mapOf(
                2 to RemoteFoodSearchPage(items = listOf(completeItem), nextPage = 3, hasMore = true)
            ),
            failingPages = setOf(1)
        )
        val viewModel = createViewModel(importRepository)

        viewModel.onSearchQueryChanged("рис")

        advanceTimeBy(900)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), importRepository.requestedPages)
        assertEquals(listOf(completeItem), viewModel.remoteFoods.value)
        assertTrue(viewModel.error.value.isNullOrBlank())
        }

    @Test
    fun `initial remote search tolerates two temporary page failures and uses third page result`() =
        runTest(mainDispatcherRule.dispatcher) {
        val completeItem = FoodSearchItem(
            barcode = "remote-3",
            name = "Суп",
            brand = "Test",
            imageUrl = null,
            caloriesPer100g = 55.0,
            proteinPer100g = 3.0,
            fatPer100g = 2.0,
            carbsPer100g = 6.0
        )
        val importRepository = PagingFoodImportRepository(
            pages = mapOf(
                3 to RemoteFoodSearchPage(items = listOf(completeItem), nextPage = 4, hasMore = true)
            ),
            failingPages = setOf(1, 2)
        )
        val viewModel = createViewModel(importRepository)

        viewModel.onSearchQueryChanged("суп")

        advanceTimeBy(900)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), importRepository.requestedPages)
        assertEquals(listOf(completeItem), viewModel.remoteFoods.value)
        assertTrue(viewModel.error.value.isNullOrBlank())
        }

    private fun createViewModel(
        importRepository: FoodImportRepository
    ): AddMealViewModel {
        val foodRepository = FakeFoodRepository()
        val mealRepository = FakeMealRepository()
        val dailyNutritionUseCase = GetDailyNutritionUseCase(
            mealRepository = mealRepository,
            foodRepository = foodRepository
        )
        val recommendationsUseCase = GetPersonalizedFoodRecommendationsUseCase(
            foodRepository = foodRepository,
            mealRepository = mealRepository,
            userRepository = FakeUserRepository(),
            favoriteFoodsRepository = FakeFavoriteFoodsRepository(),
            allergenRepository = FakeAllergenRepository(),
            nutritionTargetsResolver = {
                NutritionTargets(
                    maintenanceCalories = 2200,
                    targetCalories = 2200,
                    proteinGrams = 120,
                    fatGrams = 70,
                    carbsGrams = 260,
                    proteinGoalBasis = ProteinGoalBasis.TOTAL_BODY_WEIGHT,
                    proteinReferenceWeightKg = 75.0
                )
            },
            getDailyNutritionUseCase = dailyNutritionUseCase
        )

        return AddMealViewModel(
            foodRepository = foodRepository,
            addMealUseCase = AddMealUseCase(mealRepository),
            importFoodByBarcodeUseCase = ImportFoodByBarcodeUseCase(importRepository),
            importFoodFromSearchItemUseCase = ImportFoodFromSearchItemUseCase(importRepository),
            searchFoodsByNameUseCase = SearchFoodsByNameUseCase(importRepository),
            getPersonalizedFoodRecommendationsUseCase = recommendationsUseCase
        )
    }

    private class PagingFoodImportRepository(
        private val pages: Map<Int, RemoteFoodSearchPage>,
        private val failingPages: Set<Int> = emptySet()
    ) : FoodImportRepository {

        val requestedPages = mutableListOf<Int>()

        override suspend fun importByBarcode(barcode: String): Food {
            error("Not used in this test")
        }

        override suspend fun importFromSearchItem(item: FoodSearchItem): Food {
            error("Not used in this test")
        }

        override suspend fun searchByName(
            query: String,
            page: Int,
            pageSize: Int
        ): RemoteFoodSearchPage {
            requestedPages += page
            if (page in failingPages) {
                throw IllegalStateException("timeout: Open Food Facts search page timed out")
            }
            return pages[page]
                ?: RemoteFoodSearchPage(items = emptyList(), nextPage = null, hasMore = false)
        }
    }

    class MainDispatcherRule(
        val dispatcher: TestDispatcher = StandardTestDispatcher()
    ) : TestWatcher() {

        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
