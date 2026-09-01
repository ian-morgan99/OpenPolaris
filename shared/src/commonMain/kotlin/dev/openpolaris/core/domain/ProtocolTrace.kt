package dev.openpolaris.core.domain

/**
 * Diagnostic logger for low-level protocol traffic.
 *
 * Production runs are silent — the gate defaults to *off* so the
 * background reader loop never pays the cost of formatting strings.
 * When a user hits a protocol mismatch (e.g. 823 hello ack missing)
 * they can flip the gate on at JVM start:
 *
 *     -Dopenpolaris.protocol.trace=true
 *     # or
 *     OPENPOLARIS_PROTOCOL_TRACE=1 cli-probe auth-smoke 192.168.0.1 9090
 *
 * Output goes to stdout so it lands in `/tmp/openpolaris-desktop.log`
 * when the desktop app is launched via `scripts/run-desktop.sh`. On
 * KMP this uses `println`, which on Android maps to `Log.i` via the
 * platform's stdout bridge.
 *
 * Kept in [commonMain] (not a `expect`/`actual`) so the desktop, CLI
 * probe, Android and any future platform share one source of truth
 * for what the wire actually did.
 */
object ProtocolTrace {
    private val TRUTHY = setOf("1", "true", "yes", "on")

    // Re-checked on every call so the gate can be flipped from a test
    // (e.g. setProperty at the top of a diagnostic test). The cost is
    // two string lookups, which is negligible compared to the
    // `println` we'd skip when off.
    private fun enabled(): Boolean {
        val sys = (getSystemProperty("openpolaris.protocol.trace") ?: "").lowercase()
        if (sys.isNotEmpty()) return sys in TRUTHY
        val env = (getEnvironmentVariable("OPENPOLARIS_PROTOCOL_TRACE") ?: "").lowercase()
        return env in TRUTHY
    }

    val isOn: Boolean get() = enabled()

    fun log(tag: String, message: String) {
        if (!enabled()) return
        println("[trace:$tag] $message")
    }

    fun logBytes(tag: String, prefix: String, bytes: ByteArray, max: Int = 256) {
        if (!enabled()) return
        val shown = if (bytes.size <= max) bytes else bytes.copyOfRange(0, max)
        val hex = shown.joinToString(" ") { "%02x".format(it) }
        val tail = if (bytes.size > max) " …(+${bytes.size - max} more)" else ""
        println("[trace:$tag] $prefix ${bytes.size}B: $hex$tail")
    }
}

/**
 * Tiny abstraction so [ProtocolTrace] doesn't have to be an `expect`/`actual`
 * pair. JVM/Android use the system property + env var APIs; KMP test
 * harnesses can stub this to force the gate on.
 */
internal expect fun getSystemProperty(key: String): String?

/** Same shape as [getSystemProperty] but for env vars. */
internal expect fun getEnvironmentVariable(key: String): String?
