package com.example.foodiary.data.local.preferences

import android.content.Context

class FavoriteFoodsStorage(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isFavorite(foodId: String): Boolean {
        return getFavorites().contains(foodId)
    }

    fun toggleFavorite(foodId: String): Boolean {
        val updated = getFavorites().toMutableSet()

        val isNowFavorite = if (updated.contains(foodId)) {
            updated.remove(foodId)
            false
        } else {
            updated.add(foodId)
            true
        }

        prefs.edit()
            .putStringSet(KEY_FAVORITE_FOOD_IDS, updated)
            .apply()

        return isNowFavorite
    }

    fun getAllFavoriteIds(): Set<String> {
        return getFavorites()
    }

    fun removeFavorite(foodId: String) {
        val updated = getFavorites().toMutableSet()
        if (updated.remove(foodId)) {
            prefs.edit().putStringSet(KEY_FAVORITE_FOOD_IDS, updated).apply()
        }
    }

    private fun getFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_FOOD_IDS, emptySet()).orEmpty().toSet()
    }

    companion object {
        private const val PREFS_NAME = "favorite_foods_prefs"
        private const val KEY_FAVORITE_FOOD_IDS = "favorite_food_ids"
    }
}