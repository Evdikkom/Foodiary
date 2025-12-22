package com.example.foodiary.domain.repository

import com.example.foodiary.domain.model.Food

/**
 * FoodRepository — контракт доступа к данным о продуктах питания.
 */
interface FoodRepository {

    /**
     * Возвращает продукт по его идентификатору.
     */
    suspend fun getFoodById(foodId: String): Food
}
