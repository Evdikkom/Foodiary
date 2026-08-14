package com.example.foodiary.data.repository

import android.content.Context
import com.example.foodiary.data.local.preferences.FavoriteFoodsStorage
import com.example.foodiary.domain.repository.FavoriteFoodsRepository

class FavoriteFoodsRepositoryImpl(
    context: Context
) : FavoriteFoodsRepository {

    private val storage = FavoriteFoodsStorage(context.applicationContext)

    override fun getFavoriteFoodIds(): Set<String> {
        return storage.getAllFavoriteIds()
    }
}
