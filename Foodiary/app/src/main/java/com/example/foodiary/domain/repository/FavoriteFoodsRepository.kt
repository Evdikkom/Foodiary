package com.example.foodiary.domain.repository

interface FavoriteFoodsRepository {
    fun getFavoriteFoodIds(): Set<String>
}
