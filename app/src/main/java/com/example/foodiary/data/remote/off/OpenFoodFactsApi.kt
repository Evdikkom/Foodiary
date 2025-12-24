package com.example.foodiary.data.remote.off

import com.example.foodiary.data.remote.off.dto.OffSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    // Детали продукта по штрихкоду (barcode — штрихкод)
    @GET("api/v2/product/{barcode}")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String =
            "code,product_name,brands,nutriments"
    ): OffProductResponseDto
    // ВАЖНО: если у тебя уже есть конкретный DTO для product v2 — оставь его вместо Any.

    // Поиск по словам через v1 (cgi/search.pl) (v1 — старая стабильная форма полнотекстового поиска)
    @GET("cgi/search.pl")
    suspend fun searchProductsV1(
        @Query("search_terms") query: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("fields") fields: String =
            "code,product_name,brands,nutriments"
    ): OffSearchResponseDto
}