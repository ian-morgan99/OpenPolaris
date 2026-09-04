package dev.openpolaris.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.openpolaris.core.domain.readResourceBytes
import dev.openpolaris.core.domain.readResourceText

/**
 * In-app viewer for the bundled [USER-MANUAL.md].
 *
 * The same content is also published in the project repository
 * under `docs/USER-MANUAL.md` and surfaced in `README.md`. The
 * in-app copy is loaded via [readResourceText] / [readResourceBytes],
 * which already work on Android and JVM.
 *
 * The pane is rendered inside [CalloutDialog], which owns the only
 * bounded `verticalScroll` modifier in the UI tree (see
 * [CalloutDialogNoScrollWrapperTest]). The pane itself MUST NOT
 * introduce its own `verticalScroll`.
 */
@Composable
fun UserManualPane(modifier: Modifier = Modifier) {
    val manualText = remember { readResourceText("USER-MANUAL.md") }

    Surface(modifier = modifier.padding(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("User Manual", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bundled with the app. Latest source on GitHub: " +
                    "github.com/ian-morgan99/OpenPolaris/blob/main/docs/USER-MANUAL.md",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            if (manualText == null) {
                Text(
                    "Manual could not be loaded. Check that the build bundled " +
                        "composeApp/src/commonMain/resources/USER-MANUAL.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                ManualBody(text = manualText)
            }
        }
    }
}

@Composable
private fun ManualBody(text: String) {
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        when {
            // Skip the top-level title — the dialog title bar already
            // shows "User Manual".
            line.startsWith("# ") -> {
                while (i < lines.size && lines[i].isNotBlank()) i++
            }

            line.startsWith("## ") -> {
                Text(
                    line.removePrefix("## ").trim(),
                    style = MaterialTheme.typography.titleMedium,
                )
                i++
            }

            line.startsWith("### ") -> {
                Text(
                    line.removePrefix("### ").trim(),
                    style = MaterialTheme.typography.titleSmall,
                )
                i++
            }

            line.isBlank() -> i++

            line.startsWith("![") -> {
                val alt = line.substringAfter("![").substringBefore("]")
                val src = line.substringAfter("(").substringBefore(")")
                ManualImage(src = src, alt = alt)
                i++
            }

            line.startsWith("```") -> {
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimEnd().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                if (i < lines.size) i++
                CodeBlock(code.toString())
            }

            line.startsWith("- ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trimEnd().startsWith("- ")) {
                    items.add(lines[i].trimEnd().removePrefix("- ").trim())
                    i++
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items.forEach { item ->
                        Text(
                            "•  $item",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            else -> {
                val para = StringBuilder()
                while (i < lines.size &&
                    lines[i].isNotBlank() &&
                    !lines[i].trimEnd().startsWith("#") &&
                    !lines[i].trimEnd().startsWith("```") &&
                    !lines[i].trimEnd().startsWith("- ") &&
                    !lines[i].trimEnd().startsWith("![")
                ) {
                    if (para.isNotEmpty()) para.append(' ')
                    para.append(lines[i].trim())
                    i++
                }
                if (para.isNotEmpty()) {
                    Text(para.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ManualImage(src: String, alt: String) {
    val bitmap = remember(src) {
        val bytes = readResourceBytes(src) ?: return@remember null
        bytesToImageBitmap(bytes)
    }
    if (bitmap != null) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Text(
            "[image: $alt — not bundled: $src]",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = text.ifEmpty { " " },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
        )
    }
}
