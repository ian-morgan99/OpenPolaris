package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewPixelAnalysisTest {
    @Test
    fun uniformFrameHasStableMeanAndNoFocusPeaks() {
        val pixels = IntArray(16) { 0xff202020.toInt() }

        val result = PreviewPixelAnalyzer.analyzePixels(pixels, width = 4, height = 4, maxGridWidth = 4, maxGridHeight = 4)

        assertEquals(4, result.histogram.indexOfFirst { it == 16 })
        assertEquals(32f, result.meanLuma, 0.01f)
        assertEquals(0f, result.peakFraction)
        assertTrue(result.peakMap.all { !it })
    }

    @Test
    fun highContrastPatternProducesPeaksAndBoundedHistogram() {
        val pixels = IntArray(64) { index -> if (index % 8 < 4) 0xff000000.toInt() else 0xffffffff.toInt() }

        val result = PreviewPixelAnalyzer.analyzePixels(pixels, width = 8, height = 8, maxGridWidth = 8, maxGridHeight = 8)

        assertTrue(result.peakFraction > 0f)
        assertTrue(result.peakMap.any())
        assertEquals(64, result.histogram.sum())
        assertEquals(32, result.histogram[0])
        assertEquals(32, result.histogram.last())
    }

    @Test
    fun sampledAnalysisRemainsBoundedForLargeFrames() {
        val pixels = IntArray(100 * 80) { index -> if (index % 2 == 0) 0xff101010.toInt() else 0xffe0e0e0.toInt() }

        val result = PreviewPixelAnalyzer.analyzePixels(pixels, width = 100, height = 80, maxGridWidth = 16, maxGridHeight = 12)

        assertEquals(16, result.peakMapWidth)
        assertEquals(12, result.peakMapHeight)
        assertEquals(16 * 12, result.histogram.sum())
        assertFalse(result.peakMap.size > 16 * 12)
    }
}
