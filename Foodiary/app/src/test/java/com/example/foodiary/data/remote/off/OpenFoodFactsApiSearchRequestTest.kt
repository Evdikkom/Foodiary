package com.example.foodiary.data.remote.off

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OpenFoodFactsApiSearchRequestTest {

    private val appleRu = "\u044f\u0431\u043b\u043e\u043a\u043e"
    private val chipsRu = "\u0447\u0438\u043f\u0441\u044b"
    private val appleNameRu = "\u042f\u0431\u043b\u043e\u043a\u043e"
    private val potatoChipsNameRu =
        "\u0427\u0438\u043f\u0441\u044b \u043a\u0430\u0440\u0442\u043e\u0444\u0435\u043b\u044c\u043d\u044b\u0435"

    private lateinit var server: MockWebServer
    private lateinit var api: OpenFoodFactsApi

    @Test
    fun `factory uses Open Food Facts production deployment first`() {
        assertEquals("https://world.openfoodfacts.org/", OpenFoodFactsApiFactory.BASE_URL)
    }

    @Test
    fun `factory keeps staging deployment as fallback`() {
        assertEquals("https://world.openfoodfacts.net/", OpenFoodFactsApiFactory.FALLBACK_BASE_URL)
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenFoodFactsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `basic product queries use stable cgi search endpoint`() = runBlocking {
        val queries = listOf(appleRu, chipsRu, "apple", "chips")

        queries.forEach { query ->
            server.enqueue(searchResponse())

            api.searchProductsV1(
                query = query,
                page = 2,
                pageSize = 7
            )

            val request = server.takeRequest()
            val url = request.requestUrl ?: error("Expected request URL")

            assertEquals("/cgi/search.pl", url.encodedPath)
            assertEquals(query, url.queryParameter("search_terms"))
            assertEquals("1", url.queryParameter("search_simple"))
            assertEquals("process", url.queryParameter("action"))
            assertEquals("1", url.queryParameter("json"))
            assertEquals("2", url.queryParameter("page"))
            assertEquals("7", url.queryParameter("page_size"))
            assertEquals(null, url.queryParameter("q"))
            assertFalse(url.encodedPath.contains("/api/v2/search"))
            assertFalse(url.encodedPath == "/search")
        }
    }

    @Test
    fun `cgi search response maps basic product names and nutrition`() = runBlocking {
        server.enqueue(
            searchResponse(
                body = """
                    {
                      "count": 2,
                      "page": 1,
                      "page_size": 20,
                      "products": [
                        {
                          "code": "apple-1",
                          "product_name": "\u042f\u0431\u043b\u043e\u043a\u043e",
                          "brands": "Fresh",
                          "image_front_small_url": "https://example.test/apple.webp",
                          "nutriments": {
                            "energy-kcal_100g": 52,
                            "proteins_100g": 0.3,
                            "fat_100g": 0.2,
                            "carbohydrates_100g": 14
                          }
                        },
                        {
                          "code": "chips-1",
                          "product_name": "\u0427\u0438\u043f\u0441\u044b \u043a\u0430\u0440\u0442\u043e\u0444\u0435\u043b\u044c\u043d\u044b\u0435",
                          "brands": "Snack",
                          "image_front_small_url": "https://example.test/chips.webp",
                          "nutriments": {
                            "energy-kcal_100g": 536,
                            "proteins_100g": 6.4,
                            "fat_100g": 34,
                            "carbohydrates_100g": 53.8
                          }
                        }
                      ]
                    }
                """.trimIndent()
            )
        )

        val response = api.searchProductsV1(query = chipsRu)
        val items = response.products.mapNotNull(OffFoodMapper::toSearchItem)

        assertEquals(listOf(appleNameRu, potatoChipsNameRu), items.map { it.name })
        assertEquals(52.0, items.first().caloriesPer100g ?: 0.0, 0.001)
        assertEquals(536.0, items.last().caloriesPer100g ?: 0.0, 0.001)
        assertEquals(34.0, items.last().fatPer100g ?: 0.0, 0.001)
    }

    private fun searchResponse(
        body: String = """{"count":0,"page":1,"page_size":20,"products":[]}"""
    ): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }

}
