package com.example.foodiary.data.remote.off

import com.example.foodiary.data.remote.off.dto.OffSearchProductDto
import com.example.foodiary.data.remote.off.dto.OffSearchResponseDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

class OpenFoodFactsFallbackApiTest {

    @Test
    fun `uses fallback endpoint when preferred endpoint cannot be reached`() = runBlocking {
        val primary = RecordingApi(
            failure = UnknownHostException("world.openfoodfacts.org")
        )
        val fallback = RecordingApi(
            searchResponse = OffSearchResponseDto(
                count = 1,
                page = 1,
                pageSize = 1,
                products = listOf(OffSearchProductDto(code = "1", productName = "Fallback"))
            )
        )
        val api = OpenFoodFactsFallbackApi(
            endpoints = listOf(
                OpenFoodFactsFallbackApi.Endpoint("production", primary),
                OpenFoodFactsFallbackApi.Endpoint("staging-fallback", fallback)
            )
        )

        val response = api.searchProductsV1(query = "milk")

        assertEquals(listOf("milk"), primary.searchQueries)
        assertEquals(listOf("milk"), fallback.searchQueries)
        assertEquals("Fallback", response.products.single().productName)
    }

    @Test
    fun `remembers successful fallback endpoint for next request`() = runBlocking {
        val primary = RecordingApi(
            failure = UnknownHostException("world.openfoodfacts.org")
        )
        val fallback = RecordingApi(
            searchResponse = OffSearchResponseDto(
                count = 1,
                page = 1,
                pageSize = 1,
                products = listOf(OffSearchProductDto(code = "1", productName = "Fallback"))
            )
        )
        val api = OpenFoodFactsFallbackApi(
            endpoints = listOf(
                OpenFoodFactsFallbackApi.Endpoint("production", primary),
                OpenFoodFactsFallbackApi.Endpoint("staging-fallback", fallback)
            )
        )

        api.searchProductsV1(query = "milk")
        api.searchProductsV1(query = "rice")

        assertEquals(listOf("milk"), primary.searchQueries)
        assertEquals(listOf("milk", "rice"), fallback.searchQueries)
    }

    @Test
    fun `uses configured search endpoint preference without affecting barcode imports`() = runBlocking {
        val primary = RecordingApi()
        val fallback = RecordingApi(
            searchResponse = OffSearchResponseDto(
                count = 1,
                page = 1,
                pageSize = 1,
                products = listOf(OffSearchProductDto(code = "1", productName = "Fallback"))
            )
        )
        val api = OpenFoodFactsFallbackApi(
            endpoints = listOf(
                OpenFoodFactsFallbackApi.Endpoint("production", primary),
                OpenFoodFactsFallbackApi.Endpoint("staging-fallback", fallback)
            ),
            searchEndpointPreference = { "staging-fallback" }
        )

        val searchResponse = api.searchProductsV1(query = "soup")
        api.getProductByBarcode("123")

        assertEquals(emptyList<String>(), primary.searchQueries)
        assertEquals(listOf("soup"), fallback.searchQueries)
        assertEquals("Fallback", searchResponse.products.single().productName)
        assertEquals(listOf("123"), primary.barcodeQueries)
        assertEquals(emptyList<String>(), fallback.barcodeQueries)
    }

    @Test
    fun `does not fall back to production for no vpn staging search preference`() = runBlocking {
        val primary = RecordingApi()
        val fallback = RecordingApi(
            failure = java.net.SocketTimeoutException("timeout")
        )
        val api = OpenFoodFactsFallbackApi(
            endpoints = listOf(
                OpenFoodFactsFallbackApi.Endpoint("production", primary),
                OpenFoodFactsFallbackApi.Endpoint("staging-fallback", fallback)
            ),
            searchEndpointPreference = { "staging-fallback" }
        )

        runCatching {
            api.searchProductsV1(query = "soup")
        }

        assertEquals(emptyList<String>(), primary.searchQueries)
        assertEquals(listOf("soup"), fallback.searchQueries)
    }

    @Test
    fun `barcode import falls back when production product has incomplete nutrition`() = runBlocking {
        val primary = RecordingApi(
            barcodeResponse = OffProductResponseDto(
                status = 1,
                product = OffProductDto(productName = "No nutrition")
            )
        )
        val fallback = RecordingApi(
            barcodeResponse = completeProductResponse(productName = "Fallback nutrition")
        )
        val api = OpenFoodFactsFallbackApi(
            endpoints = listOf(
                OpenFoodFactsFallbackApi.Endpoint("production", primary),
                OpenFoodFactsFallbackApi.Endpoint("staging-fallback", fallback)
            )
        )

        val response = api.getProductByBarcode("123")

        assertEquals(listOf("123"), primary.barcodeQueries)
        assertEquals(listOf("123"), fallback.barcodeQueries)
        assertEquals("Fallback nutrition", response.product?.productName)
    }

    companion object {
        private fun completeProductResponse(productName: String = "Test"): OffProductResponseDto {
            return OffProductResponseDto(
                status = 1,
                product = OffProductDto(
                    productName = productName,
                    nutriments = OffNutrimentsDto(
                        kcal100g = 100.0,
                        proteins100g = 5.0,
                        fat100g = 2.0,
                        carbs100g = 15.0
                    )
                )
            )
        }
    }

    private class RecordingApi(
        private val failure: Throwable? = null,
        private val searchResponse: OffSearchResponseDto = OffSearchResponseDto(),
        private val barcodeResponse: OffProductResponseDto = completeProductResponse()
    ) : OpenFoodFactsApi {

        val searchQueries = mutableListOf<String>()
        val barcodeQueries = mutableListOf<String>()

        override suspend fun getProductByBarcode(
            barcode: String,
            fields: String
        ): OffProductResponseDto {
            barcodeQueries += barcode
            failure?.let { throw it }
            return barcodeResponse
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
            searchQueries += query
            failure?.let { throw it }
            return searchResponse
        }
    }
}
