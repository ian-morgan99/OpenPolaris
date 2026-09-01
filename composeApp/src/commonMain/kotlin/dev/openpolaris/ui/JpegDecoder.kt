package dev.openpolaris.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decode a JPEG byte array into a Compose [ImageBitmap].
 *
 * Implemented per platform:
 *  - jvm:     `ImageIO.read` → `BufferedImage.toComposeImageBitmap()`
 *  - android: `BitmapFactory.decodeByteArray` → `Bitmap.asImageBitmap()`
 *
 * The caller is responsible for dispatching off the main thread; this
 * function is synchronous and may be expensive for large frames.
 */
expect fun decodeJpegToImageBitmap(jpeg: ByteArray): ImageBitmap
