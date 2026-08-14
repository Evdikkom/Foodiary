package com.example.foodiary.domain.repository

import com.example.foodiary.domain.model.Food
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    suspend fun getFoodById(foodId: String): Food
    suspend fun getFoodsByIds(foodIds: List<String>): List<Food>
    suspend fun getCustomFoods(limit: Int): List<Food>
    suspend fun getFoodsForRecommendationPool(limit: Int): List<Food>
    fun searchFoods(query: String): Flow<List<Food>>
    fun getRecommendedFoods(limit: Int): Flow<List<Food>>
}
