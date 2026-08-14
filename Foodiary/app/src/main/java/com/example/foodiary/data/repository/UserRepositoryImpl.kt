package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.AllergenDao
import com.example.foodiary.data.local.dao.UserDao
import com.example.foodiary.data.local.dao.UserRestrictionDao
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.data.mapper.toEntity
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.repository.UserRepository

class UserRepositoryImpl(
    private val userDao: UserDao,
    private val allergenDao: AllergenDao,
    private val userRestrictionDao: UserRestrictionDao
) : UserRepository {

    override suspend fun getCurrentUser(): User? {
        val user = userDao.getCurrentUser()?.toDomain() ?: return null
        val allergens = allergenDao.getAllergens()
            .associateBy { it.id }
            .mapValues { it.value.toDomain() }
        val restrictions = userRestrictionDao.getRestrictionsForUser(user.id)
            .mapNotNull { entity ->
                allergens[entity.allergenId]?.let { allergen ->
                    entity.toDomain(allergen)
                }
            }
        return user.copy(restrictions = restrictions)
    }

    override suspend fun saveCurrentUser(user: User) {
        userDao.upsert(user.toEntity())
        userRestrictionDao.deleteForUser(user.id)
        if (user.restrictions.isNotEmpty()) {
            userRestrictionDao.insertAll(user.restrictions.map { it.toEntity(user.id) })
        }
    }
}
