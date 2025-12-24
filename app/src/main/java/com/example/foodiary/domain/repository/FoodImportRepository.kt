package com.example.foodiary.domain.repository

import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSearchItem

interface FoodImportRepository {
    suspend fun importByBarcode(barcode: String): Food

    suspend fun searchByName(
        query: String,
        page: Int = 1,
        pageSize: Int = 20
    ): List<FoodSearchItem>
}
