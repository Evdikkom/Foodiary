package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    override fun searchFoods(query: String): Flow<List<Food>> {
        return foodDao.searchFoods(query).map { list -> list.map { it.toDomain() } }
    }

}
