package com.alexamy.unlockcounter

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CropTest {
    @Test
    fun wideImageCropsSides() = assertArrayEquals(intArrayOf(750, 0, 1250, 1000), centerCrop(2000, 1000, 500, 1000))

    @Test
    fun tallImageCropsTopAndBottom() = assertArrayEquals(intArrayOf(0, 500, 1000, 1500), centerCrop(1000, 2000, 1000, 1000))

    @Test
    fun sameAspectKeepsWholeImage() = assertArrayEquals(intArrayOf(0, 0, 1080, 2400), centerCrop(1080, 2400, 540, 1200))
}
