package com.example.foodiary.data.remote.off

import com.example.foodiary.data.remote.off.dto.OffSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String =
            "code,product_name,brands,image_front_small_url,image_front_url,nutriments"
    ): OffProductResponseDto

    @GET("cgi/search.pl")
    suspend fun searchProductsV1(
        @Query("search_terms") query: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("fields") fields: String =
            "code,product_name,brands,image_front_small_url,image_front_url,nutriments"
    ): OffSearchResponseDto
}