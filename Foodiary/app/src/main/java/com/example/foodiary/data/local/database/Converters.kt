package com.example.foodiary.data.local.database

import androidx.room.TypeConverter
import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.data.model.AllergenPresenceType
import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.BiologicalSex
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.data.model.UserRestrictionKind
import com.example.foodiary.domain.model.MealType

class Converters {

    @TypeConverter
    fun fromBiologicalSex(value: BiologicalSex): String = value.name

    @TypeConverter
    fun toBiologicalSex(value: String): BiologicalSex =
        BiologicalSex.valueOf(value)

    @TypeConverter
    fun fromUserGoal(goal: UserGoal): String = goal.name

    @TypeConverter
    fun toUserGoal(value: String): UserGoal = UserGoal.valueOf(value)

    @TypeConverter
    fun fromActivityLevel(level: ActivityLevel): String = level.name

    @TypeConverter
    fun toActivityLevel(value: String): ActivityLevel =
        ActivityLevel.valueOf(value)

    @TypeConverter
    fun fromMealType(type: MealType): String = type.name

    @TypeConverter
    fun toMealType(value: String): MealType =
        MealType.valueOf(value)

    @TypeConverter
    fun fromAllergenPresenceType(value: AllergenPresenceType): String = value.name

    @TypeConverter
    fun toAllergenPresenceType(value: String): AllergenPresenceType =
        AllergenPresenceType.valueOf(value)

    @TypeConverter
    fun fromAllergenEvidenceType(value: AllergenEvidenceType): String = value.name

    @TypeConverter
    fun toAllergenEvidenceType(value: String): AllergenEvidenceType =
        AllergenEvidenceType.valueOf(value)

    @TypeConverter
    fun fromUserRestrictionKind(value: UserRestrictionKind): String = value.name

    @TypeConverter
    fun toUserRestrictionKind(value: String): UserRestrictionKind =
        UserRestrictionKind.valueOf(value)
}
