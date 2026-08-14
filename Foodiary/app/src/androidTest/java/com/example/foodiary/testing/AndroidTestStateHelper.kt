package com.example.foodiary.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.data.local.entity.UserEntity
import com.example.foodiary.data.local.preferences.LocalAccountPreferences
import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.BiologicalSex
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.domain.model.MealType
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object AndroidTestStateHelper {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    fun resetAll() {
        clearSharedPreferences()
        clearDatabase()
    }

    fun saveLocalAccount(
        email: String = "test@example.com",
        displayName: String = "Tester"
    ) {
        LocalAccountPreferences(context).saveAccount(email, displayName)
    }

    fun saveUser(
        entity: UserEntity = UserEntity(
            id = "current_user",
            biologicalSex = BiologicalSex.MALE,
            age = 28,
            weightKg = 78.0,
            heightCm = 180,
            bodyFatPercent = 16.0,
            goal = UserGoal.MAINTAIN_WEIGHT,
            activityLevel = ActivityLevel.ACTIVE
        )
    ) {
        runBlocking {
            AppDatabase.getInstance(context).userDao().upsert(entity)
        }
    }

    fun saveMeals(vararg meals: MealEntity) {
        saveMeals(meals.toList())
    }

    fun saveMeals(meals: List<MealEntity>) {
        if (meals.isEmpty()) return
        runBlocking {
            AppDatabase.getInstance(context).mealDao().insertAll(meals)
        }
    }

    fun saveFoods(vararg foods: FoodEntity) {
        if (foods.isEmpty()) return
        runBlocking {
            AppDatabase.getInstance(context).foodDao().insertAll(foods.toList())
        }
    }

    fun clearMeals() {
        val db = AppDatabase.getInstance(context)
        db.openHelper.writableDatabase.execSQL("DELETE FROM meals")
    }

    fun waitForSeedFoods(timeoutMs: Long = 2_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val count = runBlocking {
                AppDatabase.getInstance(context).foodDao().countFoods()
            }
            if (count > 0) return
            Thread.sleep(50L)
        }
    }

    fun getMealsCountForDay(
        dayStart: Long,
        mealType: MealType? = null
    ): Int {
        return runBlocking {
            val end = Calendar.getInstance().apply {
                timeInMillis = dayStart
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis

            AppDatabase.getInstance(context)
                .mealDao()
                .getMealsForPeriod(dayStart, end)
                .count { mealType == null || it.mealType == mealType }
        }
    }

    fun dayStart(daysOffset: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, daysOffset)
        }.timeInMillis
    }

    private fun clearSharedPreferences() {
        listOf(
            "foodiary_local_account",
            "foodiary_ui_preferences",
            "foodiary_nutrition_targets",
            "foodiary_meal_schedule",
            "foodiary_reminders",
            "favorite_foods_prefs"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private fun clearDatabase() {
        val db = AppDatabase.getInstance(context)
        db.openHelper.writableDatabase.execSQL("DELETE FROM user_restrictions")
        db.openHelper.writableDatabase.execSQL("DELETE FROM users")
        db.openHelper.writableDatabase.execSQL("DELETE FROM meals")
    }
}
