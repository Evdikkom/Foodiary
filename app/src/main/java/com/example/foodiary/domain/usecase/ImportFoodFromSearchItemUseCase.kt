package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.repository.FoodImportRepository

class ImportFoodFromSearchItemUseCase(
    private val repo: FoodImportRepository
) {
    suspend operator fun invoke(item: FoodSearchItem): Food {
        return repo.importFromSearchItem(item)
    }
}