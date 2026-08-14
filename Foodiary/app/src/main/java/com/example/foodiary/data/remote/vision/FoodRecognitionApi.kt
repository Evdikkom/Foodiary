package com.example.foodiary.data.remote.vision

import com.example.foodiary.data.remote.vision.dto.AnalyzeFoodResponseDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface FoodRecognitionApi {

    @Multipart
    @POST("api/v1/vision/analyze-food")
    suspend fun analyzeFood(
        @Part image: MultipartBody.Part
    ): AnalyzeFoodResponseDto
}
