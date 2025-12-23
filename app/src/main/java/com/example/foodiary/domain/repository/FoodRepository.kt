package com.example.foodiary.domain.repository

import kotlinx.coroutines.flow.Flow
import com.example.foodiary.domain.model.Food

interface FoodRepository {
    suspend fun getFoodById(foodId: String): Food
    fun searchFoods(query: String): Flow<List<Food>>
}