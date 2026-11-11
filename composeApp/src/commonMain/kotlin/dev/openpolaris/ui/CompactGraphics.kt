package dev.openpolaris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
    Fullscreen,
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
    Tracking,
    HalfSpeed,
    Ahrs,
    Fine,
    Apply,
    Cancel,
    Stop,
    Back,
    Forward,
}

/** The phone action footprint; the desktop rail retains the roomier target. */
@Composable
internal fun compactActionTargetSize() = if (LocalOpenPolarisCompact.current) 20.dp else 48.dp

/** Small selector used where a state choice is not safely icon-only. */
@Composable
internal fun CompactFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    graphic: OpenPolarisGraphic? = null,
    contentDescription: String? = null,
    compactLabel: String? = null,
) {
    if (LocalOpenPolarisCompact.current) {
        Box(
            modifier = modifier
                .size(if (LocalOpenPolarisCompact.current) 18.dp else 32.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else Color.Transparent,
                )
                .border(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(5.dp),
                )
                .clickable(
                    enabled = enabled,
                    role = Role.Checkbox,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription?.let { this.contentDescription = it }
                    stateDescription = if (selected) "On" else "Off"
                },
            contentAlignment = Alignment.Center,
        ) {
            if (graphic != null) {
                OpenPolarisGraphicIcon(graphic)
            } else {
                Text(
                    compactLabel ?: "•",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            label = label,
            modifier = modifier
                .height(32.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription?.let { this.contentDescription = it }
                    stateDescription = if (selected) "On" else "Off"
                },
        )
    }
}

/**
 * Material switches retain a large visual track even when the minimum
 * interactive component size is reduced. Scale only the phone rendering;
 * desktop keeps the standard switch dimensions and semantics.
 */
@Composable
internal fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!LocalOpenPolarisCompact.current) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier,
        )
        return
    }

    val interactionModifier = if (onCheckedChange == null) {
        modifier
    } else {
        modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    }
    val colors = MaterialTheme.colorScheme
    val trackColor = if (enabled) {
        if (checked) colors.primary.copy(alpha = 0.55f) else colors.outline
    } else {
        colors.onSurface.copy(alpha = 0.18f)
    }
    val thumbColor = if (enabled) {
        if (checked) colors.primary else colors.onSurfaceVariant
    } else {
        colors.onSurface.copy(alpha = 0.38f)
    }
    Canvas(
        modifier = interactionModifier
            .size(width = 30.dp, height = 20.dp)
            .semantics {
                stateDescription = if (checked) "On" else "Off"
            },
    ) {
        val trackWidth = 22.dp.toPx()
        val trackHeight = 12.dp.toPx()
        val trackLeft = (size.width - trackWidth) / 2f
        val trackTop = (size.height - trackHeight) / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(trackLeft, trackTop),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
        )
        val thumbRadius = 4.dp.toPx()
        val thumbX = if (checked) {
            trackLeft + trackWidth - thumbRadius
        } else {
            trackLeft + thumbRadius
        }
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(thumbX, size.height / 2f),
        )
    }
}

/**
 * Graphic-only tab used by the compact destination bar. Material3's
 * TextButton still carries button-specific layout semantics even when its
 * content is only an icon; this keeps the tab role and selected state while
 * leaving the parent bar in control of the 20 dp row height.
 */
@Composable
internal fun CompactGraphicTabButton(
    graphic: OpenPolarisGraphic,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        OpenPolarisGraphicIcon(
            graphic = graphic,
            tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
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

/**
 * A compact action for secondary text-button work. Phone layouts keep the
 * action name in semantics while replacing the long label with a small
 * graphic; desktop retains the familiar text button.
 */
@Composable
internal fun ResponsiveTextActionButton(
    graphic: OpenPolarisGraphic,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wideModifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.then(wideModifier).semantics {
                this.contentDescription = contentDescription
            },
        ) {
            Text(label)
        }
    }
}

/**
 * Text remains the right affordance for disambiguated results such as named
 * alignment stars. On phones it is still rendered as a short, ellipsised
 * row instead of inheriting Material3's large text-button footprint.
 */
@Composable
internal fun CompactTextButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = if (LocalOpenPolarisCompact.current) 4.dp else 8.dp, vertical = 0.dp),
        modifier = modifier
            .heightIn(max = if (LocalOpenPolarisCompact.current) 28.dp else 48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            },
    ) {
        Text(
            label,
            style = if (LocalOpenPolarisCompact.current) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Keeps single-line phone fields from inheriting Material3's 56 dp default
 * height. Supporting text is deliberately excluded from the compact cap so
 * error/help copy cannot be clipped; desktop retains the stock field size.
 */
@Composable
internal fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val compactModifier = if (LocalOpenPolarisCompact.current && singleLine && supportingText == null) {
        modifier.heightIn(max = 44.dp)
    } else {
        modifier
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = compactModifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
internal fun OpenPolarisGraphicIcon(
    graphic: OpenPolarisGraphic,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val color = tint ?: LocalContentColor.current
    Canvas(modifier.size(if (LocalOpenPolarisCompact.current) 8.dp else 20.dp)) {
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
        OpenPolarisGraphic.Fullscreen -> {
            val inset = 2.5f
            val reach = 6.5f
            drawStyledLine(color, Offset(inset, inset + reach), Offset(inset, inset), stroke)
            drawStyledLine(color, Offset(inset, inset), Offset(inset + reach, inset), stroke)
            drawStyledLine(color, Offset(size.width - inset - reach, inset), Offset(size.width - inset, inset), stroke)
            drawStyledLine(color, Offset(size.width - inset, inset), Offset(size.width - inset, inset + reach), stroke)
            drawStyledLine(color, Offset(inset, size.height - inset - reach), Offset(inset, size.height - inset), stroke)
            drawStyledLine(color, Offset(inset, size.height - inset), Offset(inset + reach, size.height - inset), stroke)
            drawStyledLine(color, Offset(size.width - inset - reach, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
            drawStyledLine(color, Offset(size.width - inset, size.height - inset - reach), Offset(size.width - inset, size.height - inset), stroke)
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
        OpenPolarisGraphic.Tracking -> {
            drawCircle(color, r * 0.78f, c, style = thin)
            drawCircle(color, r * 0.16f, c)
            arrowHead(Offset(c.x + r * 0.78f, c.y), Offset(0f, 1f), color, thin)
        }
        OpenPolarisGraphic.HalfSpeed -> {
            drawStyledLine(color, Offset(c.x - r * 0.75f, c.y - r * 0.55f), Offset(c.x + r * 0.75f, c.y - r * 0.55f), thin)
            drawStyledLine(color, Offset(c.x - r * 0.75f, c.y + r * 0.55f), Offset(c.x + r * 0.75f, c.y + r * 0.55f), thin)
            drawStyledLine(color, Offset(c.x - r * 0.62f, c.y - r * 0.85f), Offset(c.x + r * 0.62f, c.y + r * 0.85f), thin)
        }
        OpenPolarisGraphic.Ahrs -> {
            drawCircle(color, r * 0.78f, c, style = thin)
            drawStyledLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), thin)
            drawStyledLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), thin)
            drawCircle(color, r * 0.18f, c)
        }
        OpenPolarisGraphic.Fine -> {
            drawCircle(color, r * 0.2f, c, style = thin)
            drawStyledLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), thin)
            drawStyledLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), thin)
        }
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
        OpenPolarisGraphic.Back -> {
            drawStyledLine(color, Offset(c.x + r, c.y), Offset(c.x - r * 0.55f, c.y), stroke)
            arrowHead(Offset(c.x - r * 0.55f, c.y), Offset(1f, 0f), color, thin)
        }
        OpenPolarisGraphic.Forward -> {
            drawStyledLine(color, Offset(c.x - r, c.y), Offset(c.x + r * 0.55f, c.y), stroke)
            arrowHead(Offset(c.x + r * 0.55f, c.y), Offset(-1f, 0f), color, thin)
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
