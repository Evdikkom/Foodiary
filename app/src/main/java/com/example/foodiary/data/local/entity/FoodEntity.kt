package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FoodEntity — сущность базы данных,
 * представляющая продукт питания.
 * Используется как справочник продуктов.
 */
@Entity(tableName = "foods")
data class FoodEntity(

    @PrimaryKey
    val id: String,

    val name: String,

    // Пищевая ценность на 100 грамм
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double,

    // Категория продукта (овощи, фрукты, мясо и т.п.)
    val category: String = "other",

    // Флаг пользовательского продукта
    val isCustom: Boolean = false
)
