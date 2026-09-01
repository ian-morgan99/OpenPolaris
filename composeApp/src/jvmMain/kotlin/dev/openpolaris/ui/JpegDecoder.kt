package dev.openpolaris.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual fun decodeJpegToImageBitmap(jpeg: ByteArray): ImageBitmap {
    require(jpeg.isNotEmpty()) { "Empty JPEG buffer" }
    val buffered = ImageIO.read(ByteArrayInputStream(jpeg))
        ?: error("ImageIO failed to decode JPEG (${jpeg.size} bytes)")
    return buffered.toComposeImageBitmap()
}
