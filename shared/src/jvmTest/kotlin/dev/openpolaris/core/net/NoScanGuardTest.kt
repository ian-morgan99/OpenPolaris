package dev.openpolaris.core.net

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Static guard: the bridge code may not contain any Wi-Fi scanning primitives,
 * because the user disabled Wi-Fi after a scan-induced auth storm.
 *
 * If a future change needs to introduce a scan, this test must be updated
 * deliberately — not bypassed.
 */
class NoScanGuardTest {

    private val guardedFiles = listOf(
        "shared/src/jvmMain/kotlin/dev/openpolaris/core/net/WifiBridge.kt",
        "shared/src/jvmMain/kotlin/dev/openpolaris/core/net/BluetoothProbe.kt",
    )

    // Patterns that indicate an iwlist/iw/nmcli scan path. The test only
    // looks at the `WifiBridge.kt` and `BluetoothProbe.kt` source — callers
    // outside this surface (e.g. tests) are free to use them.
    private val forbidden = listOf(
        Regex("""\bnmcli\s+dev\s+wifi\b"""),
        Regex("""\bnmcli\s+-t\s+-f\s+SSID\b"""),
        Regex("""\bdev\s+wifi\s+rescan\b"""),
        Regex("""\bdev\s+wifi\s+list\b"""),
        Regex("""\bdev\s+wifi\s+connect\b"""),
        Regex("""\biwlist\b"""),
        Regex("""\biw\s+dev\b.*\bscan\b"""),
        Regex("""\biw\s+scan\b"""),
        Regex("""\bbluetoothctl\s+--timeout\b"""),
        Regex("""\bscan\s+on\b"""),
    )

    @Test
    fun `bridge sources do not contain forbidden scan patterns`() {
        // The gradle test JVM runs with cwd = the module directory (shared/),
        // so we have to walk up to the repo root before resolving the path.
        val root = Paths.get("").toAbsolutePath()
        val repoRoot = generateSequence(root) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        for (relative in guardedFiles) {
            val path = repoRoot.resolve(relative)
            assertTrue(Files.exists(path), "missing $path")
            val rawText = Files.readString(path)
            val codeOnly = stripComments(rawText)
            for (pattern in forbidden) {
                assertTrue(
                    !pattern.containsMatchIn(codeOnly),
                    "forbidden pattern ${pattern.pattern} present in $relative",
                )
            }
        }
    }

    private fun stripComments(source: String): String {
        // Remove /* ... */ block comments (including /** KDoc */) and // line
        // comments. Strings are left untouched — that's fine here because the
        // forbidden patterns are command/argument syntax, not valid inside
        // string literals we care about.
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(source, " ")
        val noLine = Regex("""//[^\n]*""").replace(noBlock, " ")
        return noLine
    }
}
