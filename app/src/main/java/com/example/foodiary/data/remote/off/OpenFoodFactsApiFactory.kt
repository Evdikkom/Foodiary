package com.example.foodiary.data.remote.off

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object OpenFoodFactsApiFactory {

    private const val BASE_URL = "https://world.openfoodfacts.net/"

    fun create(): OpenFoodFactsApi {
        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                // Рекомендация OFF: выставлять User-Agent
                .header("User-Agent", "Foodiary - Android - v1.0 - import")
                .build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(OpenFoodFactsApi::class.java)
    }
}
