package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.UserRestriction
import com.example.foodiary.domain.repository.AllergenRepository

class GetUserRestrictionsUseCase(
    private val allergenRepository: AllergenRepository
) {
    suspend operator fun invoke(): List<UserRestriction> {
        return allergenRepository.getUserRestrictions()
    }
}
