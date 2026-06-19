package com.rts.rys.ryy.drawingtogether.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoLoaderTest {

    @Test
    fun `image smaller than maxDim returns sample size 1`() {
        assertEquals(1, PhotoLoader.computeSampleSize(800, 600, maxDim = 2048))
        assertEquals(1, PhotoLoader.computeSampleSize(2048, 1024, maxDim = 2048))
    }

    @Test
    fun `image slightly larger doubles sample size`() {
        // longest = 4096, maxDim = 2048 → 1단계 다운샘플 후 longest=2048
        assertEquals(2, PhotoLoader.computeSampleSize(4096, 2048, maxDim = 2048))
    }

    @Test
    fun `image 4x larger gets sampleSize 4`() {
        assertEquals(4, PhotoLoader.computeSampleSize(8192, 4096, maxDim = 2048))
    }

    @Test
    fun `tall portrait uses height as longest edge`() {
        assertEquals(2, PhotoLoader.computeSampleSize(1500, 5000, maxDim = 2048))
    }

    @Test
    fun `square photo`() {
        assertEquals(2, PhotoLoader.computeSampleSize(5000, 5000, maxDim = 2048))
        assertEquals(4, PhotoLoader.computeSampleSize(10000, 10000, maxDim = 2048))
    }

    @Test
    fun `custom maxDim`() {
        // longest=4000, maxDim=1000 → 2단계 (4000→2000→1000) → sample 4
        assertEquals(4, PhotoLoader.computeSampleSize(4000, 2000, maxDim = 1000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `maxDim zero throws`() {
        PhotoLoader.computeSampleSize(100, 100, maxDim = 0)
    }

    @Test
    fun `result is always power of two`() {
        listOf(
            Triple(3000, 2000, 2048),
            Triple(10000, 5000, 2048),
            Triple(20000, 1000, 2048),
        ).forEach { (w, h, maxDim) ->
            val sample = PhotoLoader.computeSampleSize(w, h, maxDim)
            assertEquals("sample size $sample is not power of two", 0, sample and (sample - 1))
        }
    }
}
