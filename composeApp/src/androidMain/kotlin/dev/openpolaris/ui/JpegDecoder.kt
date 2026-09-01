package dev.openpolaris.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeJpegToImageBitmap(jpeg: ByteArray): ImageBitmap {
    require(jpeg.isNotEmpty()) { "Empty JPEG buffer" }
    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        ?: error("BitmapFactory failed to decode JPEG (${jpeg.size} bytes)")
    return bitmap.asImageBitmap()
}
