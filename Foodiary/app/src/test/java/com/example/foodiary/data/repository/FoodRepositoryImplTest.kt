package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.entity.FoodEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRepositoryImplTest {

    @Test
    fun `search transliterates latin query for local russian products`() = runBlocking {
        val dao = CapturingFoodDao(
            searchResult = listOf(
                FoodEntity(
                    id = "seed_rice",
                    name = "Рис варёный",
                    caloriesPer100g = 130.0,
                    proteinPer100g = 2.7,
                    fatPer100g = 0.3,
                    carbsPer100g = 28.0,
                    category = "grain"
                )
            )
        )
        val repository = FoodRepositoryImpl(dao)

        val foods = repository.searchFoods("ris").first()

        assertEquals("seed_rice", foods.single().id)
        assertTrue(
            "Expected latin query to include Russian search variant",
            dao.lastQueries.contains("рис")
        )
    }

    private class CapturingFoodDao(
        private val searchResult: List<FoodEntity>
    ) : FoodDao {
        var lastQueries: List<String> = emptyList()

        override fun searchFoods(
            query: String,
            altQuery: String,
            thirdQuery: String
        ): Flow<List<FoodEntity>> {
            lastQueries = listOf(query, altQuery, thirdQuery)
            return flowOf(searchResult)
        }

        override fun getRecommendedFoods(limit: Int): Flow<List<FoodEntity>> = flowOf(emptyList())

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
