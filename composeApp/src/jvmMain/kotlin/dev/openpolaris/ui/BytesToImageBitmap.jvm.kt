package dev.openpolaris.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image

actual fun bytesToImageBitmap(bytes: ByteArray): ImageBitmap? {
    val skiaImage = Image.makeFromEncoded(bytes)
    return skiaImage.asImageBitmap()
}
