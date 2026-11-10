package dev.openpolaris.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Bounded, presentation-only analysis of a decoded preview frame.
 *
 * The analysis never issues a mount or camera command. It is deliberately
 * derived from the same decoded [ImageBitmap] that is already rendered by the
 * preview, so a failed or stale analysis cannot claim that the camera
 * delivered a different frame. The source is reduced to a small luminance
 * grid to keep phone and desktop recomposition predictable.
 */
data class PreviewPixelAnalysis(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val histogram: IntArray,
    val peakMapWidth: Int,
    val peakMapHeight: Int,
    val peakMap: BooleanArray,
    val meanLuma: Float,
    val peakFraction: Float,
) {
    val histogramMax: Int get() = histogram.maxOrNull() ?: 0
}

object PreviewPixelAnalyzer {
    const val HISTOGRAM_BINS = 32
    private const val DEFAULT_GRID_WIDTH = 160
    private const val DEFAULT_GRID_HEIGHT = 120
    private const val MIN_EDGE = 24
    private const val MAX_SOURCE_PIXELS = 4_000_000

    /**
     * Read pixels off the UI thread and calculate a bounded analysis result.
     * [ImageBitmap.readPixels] may block on a GPU-backed image, so callers
     * should invoke this from their background analysis dispatcher.
     */
    fun analyze(
        image: ImageBitmap,
        maxGridWidth: Int = DEFAULT_GRID_WIDTH,
        maxGridHeight: Int = DEFAULT_GRID_HEIGHT,
    ): PreviewPixelAnalysis {
        require(maxGridWidth > 0 && maxGridHeight > 0) { "analysis grid must be positive" }
        require(image.width > 0 && image.height > 0) { "preview image must be non-empty" }
        require(image.width.toLong() * image.height <= MAX_SOURCE_PIXELS) {
            "preview image is too large for local analysis"
        }
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return analyzePixels(
            pixels = pixels,
            width = image.width,
            height = image.height,
            maxGridWidth = maxGridWidth,
            maxGridHeight = maxGridHeight,
        )
    }

    /** Pure pixel seam used by JVM tests and future platform decoders. */
    fun analyzePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        maxGridWidth: Int = DEFAULT_GRID_WIDTH,
        maxGridHeight: Int = DEFAULT_GRID_HEIGHT,
    ): PreviewPixelAnalysis {
        require(width > 0 && height > 0) { "preview dimensions must be positive" }
        require(pixels.size >= width * height) { "pixel buffer is smaller than the frame" }
        require(maxGridWidth > 0 && maxGridHeight > 0) { "analysis grid must be positive" }

        val gridWidth = min(width, maxGridWidth)
        val gridHeight = min(height, maxGridHeight)
        val luma = IntArray(gridWidth * gridHeight)
        val histogram = IntArray(HISTOGRAM_BINS)
        var lumaSum = 0L

        for (gy in 0 until gridHeight) {
            val y = ((gy * height) / gridHeight + height / (gridHeight * 2)).coerceAtMost(height - 1)
            for (gx in 0 until gridWidth) {
                val x = ((gx * width) / gridWidth + width / (gridWidth * 2)).coerceAtMost(width - 1)
                val value = lumaOf(pixels[y * width + x])
                luma[gy * gridWidth + gx] = value
                histogram[(value * HISTOGRAM_BINS / 256).coerceAtMost(HISTOGRAM_BINS - 1)]++
                lumaSum += value
            }
        }

        val peakMap = BooleanArray(luma.size)
        var peakCount = 0
        for (gy in 1 until gridHeight - 1) {
            for (gx in 1 until gridWidth - 1) {
                val centre = luma[gy * gridWidth + gx]
                val horizontal = abs(luma[gy * gridWidth + gx + 1] - luma[gy * gridWidth + gx - 1])
                val vertical = abs(luma[(gy + 1) * gridWidth + gx] - luma[(gy - 1) * gridWidth + gx])
                val diagonal = max(
                    abs(luma[(gy + 1) * gridWidth + gx + 1] - luma[(gy - 1) * gridWidth + gx - 1]),
                    abs(luma[(gy + 1) * gridWidth + gx - 1] - luma[(gy - 1) * gridWidth + gx + 1]),
                )
                // A two-sided local edge avoids marking a broad gradient as
                // focus detail. The fixed floor makes the result stable in a
                // dark frame; the local contrast test suppresses isolated
                // compression noise.
                val edge = max(horizontal, max(vertical, diagonal))
                val localContrast = max(
                    abs(centre - luma[gy * gridWidth + gx - 1]),
                    abs(centre - luma[(gy - 1) * gridWidth + gx]),
                )
                if (edge >= MIN_EDGE && localContrast >= MIN_EDGE / 2) {
                    peakMap[gy * gridWidth + gx] = true
                    peakCount++
                }
            }
        }

        val sampleCount = (gridWidth * gridHeight).coerceAtLeast(1)
        return PreviewPixelAnalysis(
            sourceWidth = width,
            sourceHeight = height,
            histogram = histogram,
            peakMapWidth = gridWidth,
            peakMapHeight = gridHeight,
            peakMap = peakMap,
            meanLuma = lumaSum.toFloat() / sampleCount,
            peakFraction = peakCount.toFloat() / sampleCount,
        )
    }

    private fun lumaOf(argb: Int): Int {
        val red = (argb ushr 16) and 0xff
        val green = (argb ushr 8) and 0xff
        val blue = argb and 0xff
        return (2126 * red + 7152 * green + 722 * blue + 5000) / 10000
    }
}
