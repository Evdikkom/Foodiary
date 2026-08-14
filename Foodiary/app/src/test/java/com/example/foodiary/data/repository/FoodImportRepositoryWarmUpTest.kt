package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.remote.off.OpenFoodFactsApi
import com.example.foodiary.data.remote.off.OffProductResponseDto
import com.example.foodiary.data.remote.off.dto.OffNutrimentsDto
import com.example.foodiary.data.remote.off.dto.OffSearchProductDto
import com.example.foodiary.data.remote.off.dto.OffSearchResponseDto
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.testing.FakeAllergenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodImportRepositoryWarmUpTest {

    @Test
    fun `name search sends direct product search without dry warm-up request`() = runBlocking {
        val api = RecordingOpenFoodFactsApi()
        val repository = FoodImportRepositoryImpl(
            api = api,
            foodDao = NoopFoodDao,
            allergenRepository = FakeAllergenRepository()
        )

        val page = repository.searchByName(
            query = "сырок",
            page = 1,
            pageSize = 8
        )

        assertEquals(listOf(8), api.searchCalls.map { it.pageSize })
        assertEquals(listOf(DEFAULT_SEARCH_FIELDS), api.searchCalls.map { it.fields })
        assertTrue(page.items.isNotEmpty())
    }

    @Test
    fun `repeated searches stay direct and do not add hidden requests`() = runBlocking {
        val api = RecordingOpenFoodFactsApi()
        val repository = FoodImportRepositoryImpl(
            api = api,
            foodDao = NoopFoodDao,
            allergenRepository = FakeAllergenRepository()
        )

        repository.searchByName(query = "рис", page = 1, pageSize = 8)
        repository.searchByName(query = "суп", page = 1, pageSize = 8)
        repository.searchByName(query = "рис", page = 1, pageSize = 8)

        assertEquals(listOf(8, 8, 8), api.searchCalls.map { it.pageSize })
    }

    private data class SearchCall(
        val query: String,
        val page: Int,
        val pageSize: Int,
        val fields: String
    )

    private class RecordingOpenFoodFactsApi : OpenFoodFactsApi {
        val searchCalls = mutableListOf<SearchCall>()

        override suspend fun getProductByBarcode(
            barcode: String,
            fields: String
        ): OffProductResponseDto {
            error("Not used in this test")
        }

        override suspend fun searchProductsV1(
            query: String,
            searchSimple: Int,
            action: String,
            json: Int,
            page: Int,
            pageSize: Int,
            fields: String
        ): OffSearchResponseDto {
            searchCalls += SearchCall(query, page, pageSize, fields)
            if (pageSize == 1 && fields == "code") {
                return OffSearchResponseDto(
                    count = 1,
                    page = page,
                    pageSize = pageSize,
                    products = emptyList()
                )
            }

            return OffSearchResponseDto(
                count = 1,
                page = page,
                pageSize = pageSize,
                products = listOf(
                    OffSearchProductDto(
                        code = "test-1",
                        productName = "Сырок",
                        brands = "Test",
                        nutriments = OffNutrimentsDto(
                            kcal100g = 320.0,
                            proteins100g = 8.0,
                            fat100g = 18.0,
                            carbs100g = 31.0
                        )
                    )
                )
            )
        }
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

    private companion object {
        const val DEFAULT_SEARCH_FIELDS =
            "code,product_name,brands,image_front_small_url,image_front_url,nutriments,allergens_tags,traces_tags"
    }
}
