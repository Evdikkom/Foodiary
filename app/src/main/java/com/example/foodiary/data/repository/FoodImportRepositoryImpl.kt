package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.data.remote.off.OffFoodMapper
import com.example.foodiary.data.remote.off.OpenFoodFactsApi
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.RemoteFoodSearchPage
import com.example.foodiary.domain.repository.FoodImportRepository
import java.net.SocketTimeoutException

class FoodImportRepositoryImpl(
    private val api: OpenFoodFactsApi,
    private val foodDao: FoodDao
) : FoodImportRepository {

    override suspend fun importByBarcode(barcode: String): Food {
        try {
            val response = api.getProductByBarcode(barcode)

            if (response.status != 1 || response.product == null) {
                throw IllegalStateException(response.statusVerbose ?: "Продукт не найден")
            }

            val entity = OffFoodMapper.toFoodEntity(barcode, response.product)
            foodDao.insertAll(listOf(entity))
            return entity.toDomain()
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("База продуктов не ответила вовремя. Попробуй ещё раз.")
        }
    }

    override suspend fun importFromSearchItem(item: FoodSearchItem): Food {
        val normalizedBarcode = item.barcode.trim()
        if (normalizedBarcode.isBlank()) {
            throw IllegalStateException("У выбранного продукта отсутствует штрихкод")
        }

        if (!hasCompleteNutrition(item)) {
            throw IllegalStateException("Нельзя добавить продукт без полных КБЖУ")
        }

        val normalizedName = item.name.trim().ifBlank {
            "Продукт $normalizedBarcode"
        }

        val entity = FoodEntity(
            id = "off_$normalizedBarcode",
            name = normalizedName,
            imageUrl = item.imageUrl?.takeIf { it.isNotBlank() },
            caloriesPer100g = item.caloriesPer100g ?: 0.0,
            proteinPer100g = item.proteinPer100g ?: 0.0,
            fatPer100g = item.fatPer100g ?: 0.0,
            carbsPer100g = item.carbsPer100g ?: 0.0
        )

        foodDao.insertAll(listOf(entity))
        return entity.toDomain()
    }

    override suspend fun searchByName(
        query: String,
        page: Int,
        pageSize: Int
    ): RemoteFoodSearchPage {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return RemoteFoodSearchPage(
                items = emptyList(),
                nextPage = null,
                hasMore = false
            )
        }

        try {
            val response = api.searchProductsV1(
                query = normalizedQuery,
                page = page,
                pageSize = pageSize
            )

            val rawProducts = response.products

            val filteredItems = rawProducts
                .mapNotNull { product ->
                    val code = product.code?.trim().orEmpty()
                    val name = product.productName?.trim().orEmpty()

                    if (code.isBlank() || name.isBlank()) {
                        return@mapNotNull null
                    }

                    val item = FoodSearchItem(
                        barcode = code,
                        name = name,
                        brand = product.brands?.trim(),
                        imageUrl = product.imageFrontSmallUrl?.trim()?.ifBlank {
                            product.imageFrontUrl?.trim()
                        } ?: product.imageFrontUrl?.trim(),
                        caloriesPer100g = product.nutriments?.kcal100g,
                        proteinPer100g = product.nutriments?.proteins100g,
                        fatPer100g = product.nutriments?.fat100g,
                        carbsPer100g = product.nutriments?.carbs100g
                    )

                    if (hasCompleteNutrition(item)) item else null
                }
                .distinctBy { it.barcode }

            val rawPageSize = (response.pageSize ?: pageSize).coerceAtLeast(1)
            val hasAnotherPageByPayload = rawProducts.size >= rawPageSize
            val totalCount = response.count ?: 0
            val consumedByRawPaging = page * rawPageSize
            val hasAnotherPageByCount = totalCount > consumedByRawPaging
            val hasMore = hasAnotherPageByPayload || hasAnotherPageByCount

            return RemoteFoodSearchPage(
                items = filteredItems,
                nextPage = if (hasMore) page + 1 else null,
                hasMore = hasMore
            )
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("База продуктов отвечает слишком долго. Пропускаю эту страницу.")
        }
    }

    private fun hasCompleteNutrition(item: FoodSearchItem): Boolean {
        return item.caloriesPer100g != null &&
                item.proteinPer100g != null &&
                item.fatPer100g != null &&
                item.carbsPer100g != null
    }
}