package com.example.foodiary.domain.repository

import com.example.foodiary.domain.model.User

interface UserRepository {
    suspend fun getCurrentUser(): User?
    suspend fun saveCurrentUser(user: User)
}
