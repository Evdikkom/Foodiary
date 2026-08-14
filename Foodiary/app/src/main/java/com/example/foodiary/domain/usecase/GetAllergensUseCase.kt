package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Allergen
import com.example.foodiary.domain.repository.AllergenRepository

class GetAllergensUseCase(
    private val allergenRepository: AllergenRepository
) {
    suspend operator fun invoke(): List<Allergen> = allergenRepository.getAllergens()
}
