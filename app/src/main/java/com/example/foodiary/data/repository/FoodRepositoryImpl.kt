package com.example.foodiary.data.local.repository

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.repository.FoodRepository

/**
 * FoodRepositoryImpl — реализация репозитория справочника продуктов.
 * Предоставляет данные о пищевой ценности продуктов.
 */
class FoodRepositoryImpl(
    private val foodDao: FoodDao
) : FoodRepository {

    /**
     * Возвращает продукт по его идентификатору.
     */
    override suspend fun getFoodById(foodId: String): Food {
        return foodDao.getFoodById(foodId)
            ?.toDomain()
            ?: throw IllegalStateException(
                "Food with id=$foodId not found in database"
            )
    }
}
