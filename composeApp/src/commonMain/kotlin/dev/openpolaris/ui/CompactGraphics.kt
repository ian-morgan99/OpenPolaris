package dev.openpolaris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Small, dependency-free symbols for controls that must remain compact on a phone. */
internal enum class OpenPolarisGraphic {
    Mission,
    Connection,
    Clock,
    Slew,
    Camera,
    Refresh,
    Plus,
    Minus,
    Preview,
    Save,
    Share,
    Protect,
    Unprotect,
    Delete,
    More,
    Tonight,
    Helpers,
    Firmware,
    View3d,
    Guide,
    Settings,
    Up,
    Down,
    Left,
    Right,
    DirectionA,
    DirectionB,
    Apply,
    Cancel,
    Stop,
}

/** The phone action footprint; the desktop rail retains the roomier target. */
@Composable
internal fun compactActionTargetSize() = if (LocalOpenPolarisCompact.current) 40.dp else 48.dp

/** Small selector used where a state choice is not safely icon-only. */
@Composable
internal fun CompactFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = label,
        modifier = modifier.height(if (LocalOpenPolarisCompact.current) 30.dp else 32.dp),
    )
}

/**
 * Keeps the phone control footprint small while retaining a semantic action.
 * The full action name remains in [contentDescription] because the symbol
 * alone is not sufficient for TalkBack, keyboard navigation or desktop use.
 */
@Composable
internal fun CompactGraphicButton(
    graphic: OpenPolarisGraphic,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(compactActionTargetSize())
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        OpenPolarisGraphicIcon(graphic)
    }
}

@Composable
internal fun ResponsiveActionButton(
    graphic: OpenPolarisGraphic,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wideModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    if (LocalOpenPolarisCompact.current) {
        CompactGraphicButton(
            graphic = graphic,
            contentDescription = contentDescription,
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        )
    } else {
        val accessibleModifier = modifier.then(wideModifier).semantics {
            this.contentDescription = contentDescription
        }
        if (outlined) {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = accessibleModifier,
            ) {
                Text(label)
            }
        } else {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = accessibleModifier,
            ) {
                Text(label)
            }
        }
    }
}

@Composable
internal fun OpenPolarisGraphicIcon(
    graphic: OpenPolarisGraphic,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val color = tint ?: LocalContentColor.current
    Canvas(modifier.size(if (LocalOpenPolarisCompact.current) 16.dp else 20.dp)) {
        drawOpenPolarisGraphic(graphic, color)
    }
}

private fun DrawScope.drawOpenPolarisGraphic(graphic: OpenPolarisGraphic, color: Color) {
    val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
    val thin = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.33f

    when (graphic) {
        OpenPolarisGraphic.Mission -> {
            drawCircle(color, r * 0.82f, c, style = thin)
            drawCircle(color, r * 0.18f, c)
            drawStyledLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), thin)
            drawStyledLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), thin)
        }
        OpenPolarisGraphic.Connection -> {
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = c - Offset(r * 1.2f, r * 0.1f),
                size = Size(r * 2.4f, r * 1.2f),
                style = stroke,
            )
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = c - Offset(r * 0.78f, -r * 0.05f),
                size = Size(r * 1.56f, r * 0.78f),
                style = thin,
            )
            drawCircle(color, r * 0.12f, c + Offset(0f, r * 0.55f))
        }
        OpenPolarisGraphic.Clock -> {
            drawCircle(color, r * 0.9f, c, style = thin)
            drawStyledLine(color, c, Offset(c.x, c.y - r * 0.62f), stroke)
            drawStyledLine(color, c, Offset(c.x + r * 0.5f, c.y + r * 0.28f), stroke)
            drawCircle(color, 1.2.dp.toPx(), c)
        }
        OpenPolarisGraphic.Slew -> {
            drawStyledLine(color, Offset(c.x, 2f), Offset(c.x, size.height - 2f), stroke)
            drawStyledLine(color, Offset(2f, c.y), Offset(size.width - 2f, c.y), stroke)
            arrowHead(Offset(c.x, 2f), Offset(0f, 1f), color, thin)
            arrowHead(Offset(c.x, size.height - 2f), Offset(0f, -1f), color, thin)
            arrowHead(Offset(2f, c.y), Offset(1f, 0f), color, thin)
            arrowHead(Offset(size.width - 2f, c.y), Offset(-1f, 0f), color, thin)
        }
        OpenPolarisGraphic.Camera -> {
            drawStyledRoundRect(color, Offset(2f, 5f), Size(size.width - 4f, size.height - 7f), 2.dp.toPx(), 2.dp.toPx(), stroke)
            drawCircle(color, r * 0.62f, c + Offset(0f, 1f), style = stroke)
            drawStyledLine(color, Offset(5f, 5f), Offset(7f, 2.5f), stroke)
            drawStyledLine(color, Offset(7f, 2.5f), Offset(11f, 2.5f), stroke)
        }
        OpenPolarisGraphic.Refresh -> {
            drawArc(
                color = color,
                startAngle = 35f,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = c - Offset(r, r),
                size = Size(r * 2f, r * 2f),
                style = stroke,
            )
            arrowHead(Offset(c.x + r * 0.82f, c.y - r * 0.55f), Offset(0.7f, -0.7f), color, thin)
        }
        OpenPolarisGraphic.Plus -> {
            drawStyledLine(color, Offset(c.x - r * 0.75f, c.y), Offset(c.x + r * 0.75f, c.y), stroke)
            drawStyledLine(color, Offset(c.x, c.y - r * 0.75f), Offset(c.x, c.y + r * 0.75f), stroke)
        }
        OpenPolarisGraphic.Minus -> {
            drawStyledLine(color, Offset(c.x - r * 0.75f, c.y), Offset(c.x + r * 0.75f, c.y), stroke)
        }
        OpenPolarisGraphic.Preview -> {
            drawStyledRoundRect(color, Offset(1.5f, 3f), Size(size.width - 3f, size.height - 6f), 2.dp.toPx(), 2.dp.toPx(), stroke)
            drawStyledLine(color, Offset(8f, 7f), Offset(14f, c.y), stroke)
            drawStyledLine(color, Offset(14f, c.y), Offset(8f, 13f), stroke)
        }
        OpenPolarisGraphic.Save -> {
            drawStyledRoundRect(color, Offset(3f, 2f), Size(size.width - 6f, size.height - 4f), 1.5.dp.toPx(), 1.5.dp.toPx(), stroke)
            drawStyledRoundRect(color, Offset(6f, 3.5f), Size(6f, 4f), 0.8.dp.toPx(), 0.8.dp.toPx(), thin)
            drawCircle(color, r * 0.35f, Offset(c.x, c.y + r * 0.55f), style = thin)
        }
        OpenPolarisGraphic.Share -> {
            val top = Offset(c.x, 4f)
            val left = Offset(4f, c.y + 3f)
            val right = Offset(size.width - 4f, c.y - 3f)
            drawStyledLine(color, left, top, thin)
            drawStyledLine(color, top, right, thin)
            drawCircle(color, 2.dp.toPx(), top, style = stroke)
            drawCircle(color, 2.dp.toPx(), left, style = stroke)
            drawCircle(color, 2.dp.toPx(), right, style = stroke)
        }
        OpenPolarisGraphic.Protect -> {
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.52f, c.y - r * 1.05f),
                size = Size(r * 1.04f, r * 1.25f),
                style = stroke,
            )
            drawStyledRoundRect(color, Offset(c.x - r * 0.8f, c.y - r * 0.1f), Size(r * 1.6f, r * 1.25f), 1.5.dp.toPx(), 1.5.dp.toPx(), stroke)
            drawCircle(color, 1.2.dp.toPx(), Offset(c.x, c.y + r * 0.4f))
        }
        OpenPolarisGraphic.Unprotect -> {
            drawArc(
                color = color,
                startAngle = 215f,
                sweepAngle = 125f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.52f, c.y - r * 1.05f),
                size = Size(r * 1.04f, r * 1.25f),
                style = stroke,
            )
            drawStyledRoundRect(color, Offset(c.x - r * 0.8f, c.y - r * 0.1f), Size(r * 1.6f, r * 1.25f), 1.5.dp.toPx(), 1.5.dp.toPx(), stroke)
            drawCircle(color, 1.2.dp.toPx(), Offset(c.x, c.y + r * 0.4f))
        }
        OpenPolarisGraphic.Delete -> {
            drawStyledRoundRect(color, Offset(c.x - r * 0.68f, c.y - r * 0.45f), Size(r * 1.36f, r * 1.5f), 1.dp.toPx(), 1.dp.toPx(), stroke)
            drawStyledLine(color, Offset(c.x - r * 0.9f, c.y - r * 0.7f), Offset(c.x + r * 0.9f, c.y - r * 0.7f), stroke)
            drawStyledLine(color, Offset(c.x - r * 0.35f, c.y - r * 0.92f), Offset(c.x + r * 0.35f, c.y - r * 0.92f), stroke)
            drawStyledLine(color, Offset(c.x - r * 0.28f, c.y - r * 0.1f), Offset(c.x - r * 0.28f, c.y + r * 0.72f), thin)
            drawStyledLine(color, Offset(c.x + r * 0.28f, c.y - r * 0.1f), Offset(c.x + r * 0.28f, c.y + r * 0.72f), thin)
        }
        OpenPolarisGraphic.More -> {
            drawCircle(color, 1.7.dp.toPx(), Offset(4f, c.y))
            drawCircle(color, 1.7.dp.toPx(), c)
            drawCircle(color, 1.7.dp.toPx(), Offset(size.width - 4f, c.y))
        }
        OpenPolarisGraphic.Tonight -> {
            drawArc(
                color = color,
                startAngle = 35f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = c - Offset(r, r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 1.8.dp.toPx()),
            )
            drawStyledLine(color, Offset(c.x + r * 0.15f, c.y - r), Offset(c.x + r * 0.85f, c.y - r * 0.25f), thin)
        }
        OpenPolarisGraphic.Helpers -> {
            drawCircle(color, r * 0.45f, c, style = thin)
            drawStyledLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), thin)
            drawStyledLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), thin)
            drawStyledLine(color, Offset(c.x - r * 0.7f, c.y - r * 0.7f), Offset(c.x + r * 0.7f, c.y + r * 0.7f), thin)
            drawStyledLine(color, Offset(c.x + r * 0.7f, c.y - r * 0.7f), Offset(c.x - r * 0.7f, c.y + r * 0.7f), thin)
        }
        OpenPolarisGraphic.Firmware -> {
            drawStyledRoundRect(color, Offset(3f, 2f), Size(size.width - 6f, size.height - 4f), 2.dp.toPx(), 2.dp.toPx(), thin)
            drawStyledLine(color, Offset(c.x, 5f), Offset(c.x, size.height - 6f), stroke)
            arrowHead(Offset(c.x, size.height - 4f), Offset(0f, -1f), color, thin)
        }
        OpenPolarisGraphic.View3d -> {
            val top = Offset(c.x, 2f)
            val left = Offset(3f, 6f)
            val right = Offset(size.width - 3f, 6f)
            val bottom = Offset(c.x, size.height - 2f)
            drawStyledLine(color, top, left, thin)
            drawStyledLine(color, top, right, thin)
            drawStyledLine(color, left, bottom, thin)
            drawStyledLine(color, right, bottom, thin)
            drawStyledLine(color, left, right, thin)
        }
        OpenPolarisGraphic.Guide -> {
            drawStyledRoundRect(color, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), 1.dp.toPx(), 1.dp.toPx(), thin)
            drawStyledLine(color, c.copy(y = 2f), c.copy(y = size.height - 2f), thin)
            drawStyledLine(color, Offset(5f, 7f), Offset(c.x - 3f, 7f), thin)
            drawStyledLine(color, Offset(5f, 11f), Offset(c.x - 3f, 11f), thin)
        }
        OpenPolarisGraphic.Settings -> {
            drawCircle(color, r * 0.55f, c, style = stroke)
            drawCircle(color, r * 0.16f, c)
            repeat(8) { i ->
                val angle = i * (kotlin.math.PI / 4.0)
                val inner = Offset(c.x + kotlin.math.cos(angle).toFloat() * r * 0.75f, c.y + kotlin.math.sin(angle).toFloat() * r * 0.75f)
                val outer = Offset(c.x + kotlin.math.cos(angle).toFloat() * r, c.y + kotlin.math.sin(angle).toFloat() * r)
                drawStyledLine(color, inner, outer, stroke)
            }
        }
        OpenPolarisGraphic.Up -> arrowGraphic(color, Offset(0f, -1f), stroke)
        OpenPolarisGraphic.Down -> arrowGraphic(color, Offset(0f, 1f), stroke)
        OpenPolarisGraphic.Left -> arrowGraphic(color, Offset(-1f, 0f), stroke)
        OpenPolarisGraphic.Right -> arrowGraphic(color, Offset(1f, 0f), stroke)
        OpenPolarisGraphic.DirectionA -> directionGraphic(color, Offset(-1f, 0f), stroke)
        OpenPolarisGraphic.DirectionB -> directionGraphic(color, Offset(1f, 0f), stroke)
        OpenPolarisGraphic.Apply -> {
            drawStyledLine(color, Offset(c.x - r, c.y), Offset(c.x - r * 0.25f, c.y + r * 0.7f), stroke)
            drawStyledLine(color, Offset(c.x - r * 0.25f, c.y + r * 0.7f), Offset(c.x + r, c.y - r), stroke)
        }
        OpenPolarisGraphic.Cancel -> {
            drawStyledLine(color, Offset(c.x - r * 0.75f, c.y - r * 0.75f), Offset(c.x + r * 0.75f, c.y + r * 0.75f), stroke)
            drawStyledLine(color, Offset(c.x + r * 0.75f, c.y - r * 0.75f), Offset(c.x - r * 0.75f, c.y + r * 0.75f), stroke)
        }
        OpenPolarisGraphic.Stop -> {
            drawCircle(color, r * 0.95f, c, style = thin)
            drawRoundRect(
                color = color,
                topLeft = c - Offset(r * 0.42f, r * 0.42f),
                size = Size(r * 0.84f, r * 0.84f),
                cornerRadius = CornerRadius(r * 0.08f, r * 0.08f),
                style = Stroke(width = stroke.width, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawStyledLine(
    color: Color,
    start: Offset,
    end: Offset,
    style: Stroke,
) {
    drawLine(color, start, end, strokeWidth = style.width, cap = style.cap)
}

private fun DrawScope.drawStyledRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    cornerRadiusX: Float,
    cornerRadiusY: Float,
    style: Stroke,
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(cornerRadiusX, cornerRadiusY),
        style = style,
    )
}

private fun DrawScope.arrowGraphic(color: Color, direction: Offset, stroke: Stroke) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val tip = c + direction * 6.dp.toPx()
    val tail = c - direction * 5.dp.toPx()
    drawStyledLine(color, tail, tip, stroke)
    val perpendicular = Offset(-direction.y, direction.x)
    drawStyledLine(color, tip, tip - direction * 4.dp.toPx() + perpendicular * 3.dp.toPx(), stroke)
    drawStyledLine(color, tip, tip - direction * 4.dp.toPx() - perpendicular * 3.dp.toPx(), stroke)
}

private fun DrawScope.directionGraphic(color: Color, direction: Offset, stroke: Stroke) {
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color, 2.dp.toPx(), c, style = stroke)
    val tip = c + direction * 6.dp.toPx()
    val tail = c + direction * 2.dp.toPx()
    drawStyledLine(color, tail, tip, stroke)
    val perpendicular = Offset(-direction.y, direction.x)
    drawStyledLine(color, tip, tip - direction * 3.dp.toPx() + perpendicular * 2.5.dp.toPx(), stroke)
    drawStyledLine(color, tip, tip - direction * 3.dp.toPx() - perpendicular * 2.5.dp.toPx(), stroke)
}

private fun DrawScope.arrowHead(tip: Offset, direction: Offset, color: Color, stroke: Stroke) {
    val perpendicular = Offset(-direction.y, direction.x)
    drawStyledLine(color, tip, tip - direction * 4.dp.toPx() + perpendicular * 2.dp.toPx(), stroke)
    drawStyledLine(color, tip, tip - direction * 4.dp.toPx() - perpendicular * 2.dp.toPx(), stroke)
}
