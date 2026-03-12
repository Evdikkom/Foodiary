package com.example.foodiary.domain.model

data class RemoteFoodSearchPage(
    val items: List<FoodSearchItem>,
    val nextPage: Int?,
    val hasMore: Boolean
)