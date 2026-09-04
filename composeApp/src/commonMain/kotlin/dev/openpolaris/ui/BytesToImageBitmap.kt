package dev.openpolaris.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decode a PNG / JPEG byte buffer into a Compose [ImageBitmap].
 *
 * Lives in the `composeApp` module because [ImageBitmap] is a Compose
 * UI type and `shared` does not depend on Compose. The Android actual
 * uses `android.graphics.BitmapFactory`; the JVM actual uses
 * `org.jetbrains.skia.Image` (pulled in transitively by the Compose
 * Desktop runtime).
 *
 * Returns `null` if the bytes are not a recognised image format.
 */
expect fun bytesToImageBitmap(bytes: ByteArray): ImageBitmap?
