package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.RemoteFoodSearchPage
import com.example.foodiary.domain.repository.FoodImportRepository

class SearchFoodsByNameUseCase(
    private val repo: FoodImportRepository
) {
    suspend operator fun invoke(
        query: String,
        page: Int = 1,
        pageSize: Int = 20
    ): RemoteFoodSearchPage {
        return repo.searchByName(
            query = query,
            page = page,
            pageSize = pageSize
        )
    }
}