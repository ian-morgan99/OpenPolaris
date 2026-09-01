package dev.openpolaris.core.domain

/**
 * Platform-agnostic contract for the MJPEG-over-HTTP transport that
 * pulls a `multipart/x-mixed-replace; boundary=…` stream from the
 * mount and hands each JPEG part to [onFrame] one at a time.
 *
 * The implementation is platform-specific (JVM/Android use
 * `java.net.HttpURLConnection`; iOS would use `NSURLSession`; etc.).
 * Construct one with [createPreviewTransport] so each platform can
 * supply its own `actual`.
 *
 * The transport is intentionally simple: no persistent connection
 * cache, no chunked-transfer edge cases (the response uses
 * Content-Length: 0 and the boundary is the real delimiter), and no
 * backpressure — [onFrame] is invoked from a worker thread and is
 * allowed to drop frames by returning `false`. The caller is expected
 * to feed frames into a `StateFlow<ImageBitmap?>` and let conflation
 * drop late frames.
 *
 * Spec: ARCHITECTURE.md §3.4.
 */
interface PreviewTransport {
    fun start(host: String, port: Int = 8080, path: String = "/?action=stream")
    fun stop()
}

/** Build a [PreviewTransport] backed by the host platform's HTTP client. */
expect fun createPreviewTransport(
    onFrame: (ByteArray) -> Boolean,
    onError: (Throwable) -> Unit = {},
): PreviewTransport
