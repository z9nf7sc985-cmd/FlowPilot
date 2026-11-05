package com.flowpilot.automation.image

import org.junit.Assert.*
import org.junit.Test

class ImageMatcherRegionTest {
    @Test
    fun parseRegion_blank_returnsNull() {
        assertNull(ImageMatcher.parseRegion("", 100, 200))
        assertNull(ImageMatcher.parseRegion("   ", 100, 200))
    }

    @Test
    fun parseRegion_invalid_returnsNull() {
        assertNull(ImageMatcher.parseRegion("1,2,3", 100, 200))
        assertNull(ImageMatcher.parseRegion("a,b,c,d", 100, 200))
    }

    @Test
    fun parseRegion_clamps() {
        val r = ImageMatcher.parseRegion("10, 20, 80, 150", 100, 200)!!
        assertEquals(10, r.left)
        assertEquals(20, r.top)
        assertEquals(80, r.right)
        assertEquals(150, r.bottom)
    }

    @Test
    fun parseRegion_outOfBounds_clamped() {
        val r = ImageMatcher.parseRegion("-5, -5, 999, 999", 100, 200)!!
        assertEquals(0, r.left)
        assertEquals(0, r.top)
        assertEquals(100, r.right)
        assertEquals(200, r.bottom)
    }
}
