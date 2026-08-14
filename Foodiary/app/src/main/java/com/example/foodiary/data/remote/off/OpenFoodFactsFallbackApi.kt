package com.example.foodiary.data.remote.off

import com.example.foodiary.data.remote.off.dto.OffSearchResponseDto
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

internal class OpenFoodFactsFallbackApi(
    private val endpoints: List<Endpoint>,
    private val searchEndpointPreference: (() -> String?)? = null
) : OpenFoodFactsApi {

    private companion object {
        private const val PRODUCTION_ENDPOINT_NAME = "production"
    }

    init {
        require(endpoints.isNotEmpty()) { "At least one Open Food Facts endpoint is required" }
    }

    @Volatile
    private var preferredDefaultEndpointIndex = 0

    @Volatile
    private var preferredSearchEndpointIndex = 0

    private val cooldownUntilByIndex = LongArray(endpoints.size)

    override suspend fun getProductByBarcode(
        barcode: String,
        fields: String
    ): OffProductResponseDto {
        return executeWithFallback(
            requestKind = RequestKind.Default,
            preferredEndpointName = PRODUCTION_ENDPOINT_NAME
        ) { api ->
            val response = api.getProductByBarcode(
                barcode = barcode,
                fields = fields
            )
            if (response.status == 1 && response.product != null && !hasCompleteNutrition(response.product)) {
                throw IncompleteBarcodeNutritionException(barcode)
            }
            response
        }
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
        val preferredEndpointName = searchEndpointPreference?.invoke()
        OffNetworkDebugLogger.log(
            "OFF search endpoint preference endpoint=${preferredEndpointName ?: "memory"}"
        )

        return executeWithFallback(
            requestKind = RequestKind.Search,
            preferredEndpointName = preferredEndpointName,
            allowFallbackToOtherEndpoints = preferredEndpointName != "staging-fallback"
        ) { api ->
            api.searchProductsV1(
                query = query,
                searchSimple = searchSimple,
                action = action,
                json = json,
                page = page,
                pageSize = pageSize,
                fields = fields
            )
        }
    }

    private suspend fun <T> executeWithFallback(
        requestKind: RequestKind,
        preferredEndpointName: String? = null,
        allowFallbackToOtherEndpoints: Boolean = true,
        request: suspend (OpenFoodFactsApi) -> T
    ): T {
        var lastFailure: Throwable? = null
        val rememberedEndpointIndex = rememberedEndpointIndex(requestKind)
        val orderedEndpoints = endpointOrder(
            preferredEndpointName = preferredEndpointName,
            rememberedEndpointIndex = rememberedEndpointIndex,
            allowFallbackToOtherEndpoints = allowFallbackToOtherEndpoints
        )
        val startedAtNanos = System.nanoTime()

        for ((orderPosition, endpointIndex) in orderedEndpoints.withIndex()) {
            val endpoint = endpoints[endpointIndex]
            try {
                OffNetworkDebugLogger.log(
                    "OFF fallback attempt endpoint=${endpoint.name} index=$endpointIndex preferred=$rememberedEndpointIndex " +
                        "requestedPreferred=${preferredEndpointName ?: "memory"} " +
                        "cooldownUntil=${cooldownUntilByIndex[endpointIndex]}"
                )
                val result = request(endpoint.apiFor(requestKind))
                rememberSuccessfulEndpoint(requestKind, endpointIndex)
                val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000
                OffNetworkDebugLogger.log(
                    "OFF fallback success endpoint=${endpoint.name} index=$endpointIndex bodyParsed=true elapsedMs=$elapsedMs"
                )
                return result
            } catch (error: Throwable) {
                if (error is CancellationException) throw error

                lastFailure = error
                val fallbackWorthy = isFallbackWorthy(error)
                if (fallbackWorthy) {
                    putEndpointOnCooldown(endpointIndex)
                }
                val canTryNext = orderPosition < orderedEndpoints.lastIndex
                if (!canTryNext || !fallbackWorthy) {
                    OffNetworkDebugLogger.log(
                        "OFF fallback stop endpoint=${endpoint.name} canTryNext=$canTryNext fallbackWorthy=$fallbackWorthy",
                        error
                    )
                    throw error
                }
                OffNetworkDebugLogger.log(
                    "OFF fallback switching from endpoint=${endpoint.name} cooldownUntil=${cooldownUntilByIndex[endpointIndex]}",
                    error
                )
            }
        }

        throw lastFailure ?: IllegalStateException("Open Food Facts request failed")
    }

    private fun endpointOrder(
        preferredEndpointName: String?,
        rememberedEndpointIndex: Int,
        allowFallbackToOtherEndpoints: Boolean
    ): List<Int> {
        val preferredByName = preferredEndpointName?.let { name ->
            endpoints.indexOfFirst { endpoint -> endpoint.name == name }.takeIf { it >= 0 }
        }
        val preferred = (preferredByName ?: rememberedEndpointIndex).coerceIn(endpoints.indices)
        val baseOrder = if (allowFallbackToOtherEndpoints) {
            listOf(preferred) + endpoints.indices.filterNot { it == preferred }
        } else {
            listOf(preferred)
        }
        val now = System.currentTimeMillis()
        val availableOrder = baseOrder.filter { index -> cooldownUntilByIndex[index] <= now }
        return availableOrder.ifEmpty { baseOrder }
    }

    private fun rememberedEndpointIndex(requestKind: RequestKind): Int {
        return when (requestKind) {
            RequestKind.Default -> preferredDefaultEndpointIndex
            RequestKind.Search -> preferredSearchEndpointIndex
        }
    }

    private fun rememberSuccessfulEndpoint(requestKind: RequestKind, endpointIndex: Int) {
        when (requestKind) {
            RequestKind.Default -> preferredDefaultEndpointIndex = endpointIndex
            RequestKind.Search -> preferredSearchEndpointIndex = endpointIndex
        }
    }

    private fun putEndpointOnCooldown(endpointIndex: Int) {
        val cooldownMillis = endpoints[endpointIndex].failureCooldownMillis
        if (cooldownMillis <= 0L) return
        cooldownUntilByIndex[endpointIndex] = System.currentTimeMillis() + cooldownMillis
    }

    private fun isFallbackWorthy(error: Throwable): Boolean {
        return when (error) {
            is IOException -> true
            is JsonParseException -> true
            is HttpException -> error.code() in 400..599
            else -> false
        }
    }

    data class Endpoint(
        val name: String,
        val api: OpenFoodFactsApi,
        val barcodeApi: OpenFoodFactsApi = api,
        val failureCooldownMillis: Long = 0L
    )

    private fun Endpoint.apiFor(requestKind: RequestKind): OpenFoodFactsApi {
        return when (requestKind) {
            RequestKind.Default -> barcodeApi
            RequestKind.Search -> api
        }
    }

    private fun hasCompleteNutrition(product: OffProductDto): Boolean {
        val nutriments = product.nutriments ?: return false
        val hasEnergy = nutriments.kcal100g != null || nutriments.kj100g != null
        return hasEnergy &&
            nutriments.proteins100g != null &&
            nutriments.fat100g != null &&
            nutriments.carbs100g != null
    }

    private class IncompleteBarcodeNutritionException(
        barcode: String
    ) : IOException(
        "\u0423 \u043f\u0440\u043e\u0434\u0443\u043a\u0442\u0430 $barcode \u0432 Open Food Facts \u043d\u0435\u0442 \u043f\u043e\u043b\u043d\u043e\u0433\u043e \u041a\u0411\u0416\u0423"
    )

    private enum class RequestKind {
        Default,
        Search
    }
}
