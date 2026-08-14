package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.data.remote.off.OffFoodMapper
import com.example.foodiary.data.remote.off.OffNetworkDebugLogger
import com.example.foodiary.data.remote.off.OpenFoodFactsApi
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.RemoteFoodSearchPage
import com.example.foodiary.domain.repository.AllergenRepository
import com.example.foodiary.domain.repository.FoodImportRepository
import java.net.SocketTimeoutException
import java.util.Locale
import retrofit2.HttpException

class FoodImportRepositoryImpl(
    private val api: OpenFoodFactsApi,
    private val foodDao: FoodDao,
    private val allergenRepository: AllergenRepository
) : FoodImportRepository {

    override suspend fun importByBarcode(barcode: String): Food {
        try {
            OffNetworkDebugLogger.log("OFF barcode import start barcode=$barcode")
            val response = api.getProductByBarcode(barcode)
            OffNetworkDebugLogger.log(
                "OFF barcode import response barcode=$barcode status=${response.status} hasProduct=${response.product != null}"
            )

            if (response.status != 1 || response.product == null) {
                throw IllegalStateException(response.statusVerbose ?: "Продукт не найден")
            }

            val entity = OffFoodMapper.toFoodEntity(barcode, response.product)
            foodDao.insertAll(listOf(entity))
            allergenRepository.applyImportedAllergens(
                foodId = entity.id,
                foodName = entity.name,
                allergenTags = response.product.allergensTags.orEmpty(),
                traceTags = response.product.tracesTags.orEmpty()
            )
            return entity.toDomain()
        } catch (e: SocketTimeoutException) {
            OffNetworkDebugLogger.log("OFF timeout in FoodImportRepositoryImpl", e)
            throw IllegalStateException("База продуктов не ответила вовремя. Попробуй еще раз.")
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

        val enrichedProduct = runCatching {
            api.getProductByBarcode(normalizedBarcode).product
        }.getOrNull()

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
        allergenRepository.applyImportedAllergens(
            foodId = entity.id,
            foodName = entity.name,
            allergenTags = item.allergenTags.ifEmpty {
                enrichedProduct?.allergensTags.orEmpty()
            },
            traceTags = item.traceTags.ifEmpty {
                enrichedProduct?.tracesTags.orEmpty()
            }
        )
        return entity.toDomain()
    }

    override suspend fun searchByName(
        query: String,
        page: Int,
        pageSize: Int
    ): RemoteFoodSearchPage {
        val normalizedQuery = normalizeRemoteQuery(query.trim())
        if (normalizedQuery.isBlank()) {
            return RemoteFoodSearchPage(
                items = emptyList(),
                nextPage = null,
                hasMore = false
            )
        }

        try {
            OffNetworkDebugLogger.log(
                "OFF search start query=$normalizedQuery page=$page pageSize=$pageSize"
            )
            val response = api.searchProductsV1(
                query = normalizedQuery,
                page = page,
                pageSize = pageSize
            )

            val pageResult = buildSearchPage(
                rawProducts = response.products,
                page = page,
                requestedPageSize = pageSize,
                responsePageSize = response.pageSize,
                totalCount = response.count,
                pageCount = null
            )
            OffNetworkDebugLogger.log(
                "OFF search parsed query=$normalizedQuery page=$page rawProducts=${response.products.size} " +
                    "items=${pageResult.items.size} hasMore=${pageResult.hasMore} nextPage=${pageResult.nextPage}"
            )
            return pageResult
        } catch (e: SocketTimeoutException) {
            OffNetworkDebugLogger.log(
                "OFF search timeout query=$normalizedQuery page=$page pageSize=$pageSize",
                e
            )
            throw IllegalStateException("timeout: Open Food Facts search page timed out")
        } catch (e: HttpException) {
            OffNetworkDebugLogger.log(
                "OFF search http error query=$normalizedQuery page=$page code=${e.code()}",
                e
            )
            if (e.code() in RETRYABLE_SEARCH_HTTP_CODES) {
                throw IllegalStateException("timeout: Open Food Facts search page is temporarily unavailable")
            }
            throw e
        } catch (e: Exception) {
            OffNetworkDebugLogger.log(
                "OFF search failed query=$normalizedQuery page=$page pageSize=$pageSize",
                e
            )
            throw e
        }
    }

    private fun buildSearchPage(
        rawProducts: List<com.example.foodiary.data.remote.off.dto.OffSearchProductDto>,
        page: Int,
        requestedPageSize: Int,
        responsePageSize: Int?,
        totalCount: Int?,
        pageCount: Int?
    ): RemoteFoodSearchPage {
        val filteredItems = rawProducts
            .mapNotNull(OffFoodMapper::toSearchItem)
            .filter { hasCompleteNutrition(it) }
            .distinctBy { it.barcode }

        val rawPageSize = (responsePageSize ?: requestedPageSize).coerceAtLeast(1)
        val hasAnotherPageByPayload = rawProducts.size >= rawPageSize
        val consumedByRawPaging = page * rawPageSize
        val hasAnotherPageByCount = (totalCount ?: 0) > consumedByRawPaging
        val hasAnotherPageByPageCount = pageCount?.let { page < it } ?: false
        val hasMore = hasAnotherPageByPayload || hasAnotherPageByCount || hasAnotherPageByPageCount

        return RemoteFoodSearchPage(
            items = filteredItems,
            nextPage = if (hasMore) page + 1 else null,
            hasMore = hasMore
        )
    }

    private fun hasCompleteNutrition(item: FoodSearchItem): Boolean {
        return item.caloriesPer100g != null &&
            item.proteinPer100g != null &&
            item.fatPer100g != null &&
            item.carbsPer100g != null
    }

    companion object {
        private val RETRYABLE_SEARCH_HTTP_CODES = setOf(429, 502, 503, 504)
        private val REMOTE_QUERY_ALIASES = mapOf(
            "ris" to "рис",
            "rice" to "рис",
            "grechka" to "гречка",
            "kurica" to "курица",
            "chicken" to "курица",
            "tvorog" to "творог",
            "ovsyanka" to "овсянка",
            "oatmeal" to "овсянка"
        )
    }

    private fun normalizeRemoteQuery(raw: String): String {
        if (raw.isBlank()) return raw
        return REMOTE_QUERY_ALIASES[raw.lowercase(Locale("ru"))] ?: raw
    }
}
