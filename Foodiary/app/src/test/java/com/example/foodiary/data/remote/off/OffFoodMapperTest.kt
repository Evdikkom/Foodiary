package com.example.foodiary.data.remote.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OffFoodMapperTest {

    @Test
    fun `normalizes staging image host to production image host`() {
        val url = "https://images.openfoodfacts.net/images/products/505/399/016/1669/front_en.185.200.jpg"

        assertEquals(
            "https://images.openfoodfacts.org/images/products/505/399/016/1669/front_en.185.200.jpg",
            OffFoodMapper.normalizeImageUrl(url)
        )
    }

    @Test
    fun `blank image url stays absent`() {
        assertNull(OffFoodMapper.normalizeImageUrl(" "))
    }
}
