package com.example.foodiary.presentation.fragment

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DailyNutritionFragmentSelectedDayResolverTest {

    @Test
    fun `resolver uses current day when saved and arg day are absent`() {
        val now = calendarOf(2026, Calendar.APRIL, 27, 19, 45).timeInMillis

        val result = DailyNutritionFragment.resolveInitialSelectedDayStart(
            savedDay = null,
            argDay = null,
            nowMillis = now
        )

        assertEquals(calendarOf(2026, Calendar.APRIL, 27, 0, 0).timeInMillis, result)
    }

    @Test
    fun `resolver prefers saved day over fragment args`() {
        val saved = calendarOf(2026, Calendar.MAY, 2, 14, 0).timeInMillis
        val arg = calendarOf(2026, Calendar.APRIL, 20, 9, 0).timeInMillis

        val result = DailyNutritionFragment.resolveInitialSelectedDayStart(
            savedDay = saved,
            argDay = arg,
            nowMillis = calendarOf(2026, Calendar.JANUARY, 1, 12, 0).timeInMillis
        )

        assertEquals(calendarOf(2026, Calendar.MAY, 2, 0, 0).timeInMillis, result)
    }

    @Test
    fun `resolver normalizes explicit argument to start of day`() {
        val arg = calendarOf(2026, Calendar.JUNE, 10, 22, 13).timeInMillis

        val result = DailyNutritionFragment.resolveInitialSelectedDayStart(
            savedDay = null,
            argDay = arg
        )

        assertEquals(calendarOf(2026, Calendar.JUNE, 10, 0, 0).timeInMillis, result)
    }

    private fun calendarOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
