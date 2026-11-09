package com.alexamy.unlockcounter

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CropTest {
    @Test
    fun wideImageCropsSides() = assertArrayEquals(intArrayOf(750, 0, 1250, 1000), cropToFill(2000, 1000, 500, 1000))

    @Test
    fun wideImageSlidesFromLeftToRight() {
        assertArrayEquals(intArrayOf(0, 0, 500, 1000), cropToFill(2000, 1000, 500, 1000, 0f))
        assertArrayEquals(intArrayOf(1500, 0, 2000, 1000), cropToFill(2000, 1000, 500, 1000, 1f))
    }

    @Test
    fun tallImageCropsTopAndBottom() = assertArrayEquals(intArrayOf(0, 500, 1000, 1500), cropToFill(1000, 2000, 1000, 1000))

    @Test
    fun sameAspectKeepsWholeImage() = assertArrayEquals(intArrayOf(0, 0, 1080, 2400), cropToFill(1080, 2400, 540, 1200))
}
