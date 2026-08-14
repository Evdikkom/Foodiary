package com.example.foodiary.data.remote.off

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.repository.FoodImportRepositoryImpl
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase
import com.example.foodiary.testing.FakeAllergenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OpenFoodFactsLiveSearchDiagnosticTest {

    private val appleRu = "\u044f\u0431\u043b\u043e\u043a\u043e"
    private val chipsRu = "\u0447\u0438\u043f\u0441\u044b"
    private val soupRu = "\u0441\u0443\u043f"
    private val riceRu = "\u0440\u0438\u0441"

    @Test
    fun `exact app api client returns products for basic queries`() = runBlocking {
        assumeTrue(isLiveOffDiagnosticsEnabled())

        val api = OpenFoodFactsApiFactory.create(context = null)
        val queries = listOf(appleRu, chipsRu, soupRu, riceRu)

        queries.forEach { query ->
            val response = api.searchProductsV1(
                query = query,
                page = 1,
                pageSize = 5
            )

            assertTrue(
                "Expected Open Food Facts to return products for '$query'",
                response.products.isNotEmpty()
            )
        }
    }

    @Test
    fun `repository search path used by add meal returns complete products`() = runBlocking {
        assumeTrue(isLiveOffDiagnosticsEnabled())

        val repository = FoodImportRepositoryImpl(
            api = OpenFoodFactsApiFactory.create(context = null),
            foodDao = NoopFoodDao,
            allergenRepository = FakeAllergenRepository()
        )
        val useCase = SearchFoodsByNameUseCase(repository)
        val queries = listOf(appleRu, chipsRu, soupRu, riceRu)

        queries.forEach { query ->
            val page = useCase(
                query = query,
                page = 1,
                pageSize = 24
            )

            assertTrue(
                "Expected repository to keep complete calories/protein/fat/carbs for '$query'",
                page.items.isNotEmpty()
            )
            assertTrue(
                "Expected every item for '$query' to have complete nutrition",
                page.items.all {
                    it.caloriesPer100g != null &&
                        it.proteinPer100g != null &&
                        it.fatPer100g != null &&
                        it.carbsPer100g != null
                }
            )
        }
    }

    private fun isLiveOffDiagnosticsEnabled(): Boolean {
        return System.getProperty("foodiary.off.live") == "true"
    }

    private object NoopFoodDao : FoodDao {
        override fun searchFoods(
            query: String,
            altQuery: String,
            thirdQuery: String
        ): Flow<List<FoodEntity>> = emptyFlow()

        override fun getRecommendedFoods(limit: Int): Flow<List<FoodEntity>> = emptyFlow()

        override suspend fun getCustomFoods(limit: Int): List<FoodEntity> = emptyList()

        override suspend fun getFoodById(foodId: String): FoodEntity? = null

        override suspend fun getFoodsByIds(foodIds: List<String>): List<FoodEntity> = emptyList()

        override suspend fun insertAll(foods: List<FoodEntity>) = Unit

        override suspend fun insertFood(food: FoodEntity) = Unit

        override suspend fun deleteFoodById(foodId: String) = Unit

        override suspend fun getFoodsForPicker(limit: Int): List<FoodEntity> = emptyList()

        override suspend fun searchFoodsForPicker(
            query: String,
            altQuery: String,
            thirdQuery: String,
            limit: Int
        ): List<FoodEntity> = emptyList()

        override suspend fun countFoods(): Int = 0
    }
}
