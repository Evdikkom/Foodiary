package com.example.foodiary.data.local.database

import androidx.room.TypeConverter
import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.domain.model.MealType

/**
 * Converters — набор TypeConverter'ов для Room.
 * Обеспечивает сохранение enum-классов в базе данных.
 */
class Converters {

    // UserGoal
    @TypeConverter
    fun fromUserGoal(goal: UserGoal): String = goal.name

    @TypeConverter
    fun toUserGoal(value: String): UserGoal = UserGoal.valueOf(value)

    // ActivityLevel
    @TypeConverter
    fun fromActivityLevel(level: ActivityLevel): String = level.name

    @TypeConverter
    fun toActivityLevel(value: String): ActivityLevel =
        ActivityLevel.valueOf(value)

    // MealType
    @TypeConverter
    fun fromMealType(type: MealType): String = type.name

    @TypeConverter
    fun toMealType(value: String): MealType =
        MealType.valueOf(value)
}
