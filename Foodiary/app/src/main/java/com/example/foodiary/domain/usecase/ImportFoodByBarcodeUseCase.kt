package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.repository.FoodImportRepository

class ImportFoodByBarcodeUseCase(
    private val repo: FoodImportRepository
) {
    suspend operator fun invoke(barcode: String): Food =
        repo.importByBarcode(barcode)
}
