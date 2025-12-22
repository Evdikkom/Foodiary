package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.foodiary.domain.model.MealType

/**
 * MealEntity — сущность базы данных,
 * представляющая приём пищи пользователя.
 */
@Entity(tableName = "meals")
data class MealEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Идентификатор продукта из справочника FoodEntity
    val foodId: String,

    // Количество продукта в граммах
    val quantityInGrams: Double,

    // Тип приёма пищи (завтрак, обед и т.д.)
    val mealType: MealType,

    // Временная метка приёма пищи
    val timestamp: Long,

    // Комментарий пользователя
    val note: String = ""
)
