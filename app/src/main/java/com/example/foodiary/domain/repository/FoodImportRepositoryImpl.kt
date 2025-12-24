package com.example.foodiary.domain.repository

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.data.remote.off.OffFoodMapper
import com.example.foodiary.data.remote.off.OpenFoodFactsApi
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSearchItem

class FoodImportRepositoryImpl(
    private val api: OpenFoodFactsApi,
    private val foodDao: FoodDao
) : FoodImportRepository {

    override suspend fun importByBarcode(barcode: String): Food {
        val response = api.getProductByBarcode(barcode)

        if (response.status != 1 || response.product == null) {
            throw IllegalStateException(response.statusVerbose ?: "Продукт не найден")
        }

        val entity = OffFoodMapper.toFoodEntity(barcode, response.product)

        // важно: у тебя уже есть insertAll — используем его
        foodDao.insertAll(listOf(entity))

        return entity.toDomain()
    }

    override suspend fun searchByName(
        query: String,
        page: Int,
        pageSize: Int
    ): List<FoodSearchItem> {
        val resp = api.searchProductsV1(
            query = query,
            page = page,
            pageSize = pageSize
        )

        return resp.products.mapNotNull { p ->
            val code = p.code?.trim().orEmpty()
            val name = p.productName?.trim().orEmpty()
            if (code.isBlank() || name.isBlank()) return@mapNotNull null

            FoodSearchItem(
                barcode = code,
                name = name,
                brand = p.brands?.trim(),
                caloriesPer100g = p.nutriments?.kcal100g,
                proteinPer100g = p.nutriments?.proteins100g,
                fatPer100g = p.nutriments?.fat100g,
                carbsPer100g = p.nutriments?.carbs100g
            )
        }
    }
}
