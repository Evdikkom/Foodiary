package com.example.foodiary.presentation.util

import com.example.foodiary.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealTypeUiTest {

    @Test
    fun `primary meals no longer force snack`() {
        val primary = primaryMealTypes()

        assertTrue(MealType.BREAKFAST in primary)
        assertTrue(MealType.LUNCH in primary)
        assertTrue(MealType.DINNER in primary)
        assertFalse(MealType.SNACK in primary)
    }

    @Test
    fun `configurable meals include snack and extra slots`() {
        val configurable = configurableMealTypes()

        assertTrue(MealType.SNACK in configurable)
        assertTrue(MealType.AFTERNOON_SNACK in configurable)
        assertTrue(MealType.LATE_DINNER in configurable)
    }

    @Test
    fun `recommendation buckets map extra meals to closest core bucket`() {
        assertEquals(MealType.SNACK, MealType.AFTERNOON_SNACK.recommendationBucket())
        assertEquals(MealType.DINNER, MealType.LATE_DINNER.recommendationBucket())
        assertEquals(MealType.BREAKFAST, MealType.BREAKFAST.recommendationBucket())
    }
}
