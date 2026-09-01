package dev.openpolaris.core.solver

/**
 * Convert a raw camera frame into a list of [StarDetection]s the plate solver
 * can consume. Pixel coordinates are in the same frame as the [frameWidth]
 * x [frameHeight] bounds.
 *
 * The detector is **platform-specific** by design: the Android target needs
 * a CameraX / ImageAnalysis-backed implementation; the Compose Desktop
 * target can either run a CPU-based detector on the JPEG bytes, or stub it
 * out via [NullStarDetector] for tests.
 *
 * Threading: implementations must be safe to call from the caller's coroutine
 * — typically IO-dispatched for the heavy work.
 */
interface StarDetector {
    /**
     * @param frame encoded image bytes (e.g. the latest JPEG from
     *   [dev.openpolaris.core.domain.PreviewController]).
     * @param frameWidth width of the frame in pixels
     * @param frameHeight height of the frame in pixels
     */
    suspend fun detect(frame: ByteArray, frameWidth: Int, frameHeight: Int): List<StarDetection>
}

/**
 * Stub [StarDetector] that always returns an empty list. Use in tests and on
 * platforms that have not yet wired a real detector (currently Compose
 * Desktop). Plate-solving on those platforms becomes a no-op until a real
 * implementation lands.
 */
object NullStarDetector : StarDetector {
    override suspend fun detect(
        frame: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
    ): List<StarDetection> = emptyList()
}
