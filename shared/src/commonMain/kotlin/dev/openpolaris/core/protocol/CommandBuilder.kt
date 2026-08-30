package dev.openpolaris.core.protocol

/**
 * Builds wire-format request frames: `1&<code>&<type>&<payload>#`.
 * Payload is a `key:value;` string, or [EMPTY_CONTENT] when absent.
 *
 * The subtype slot is always [REQUEST_TYPE] (= 2) under [build] and
 * under the top-level [command] helper. That keeps every captured-wire
 * test pin green (see [Codes] header).
 *
 * If a future test demands subtype 3 for gimbal-motion codes, prefer
 * [commandWithSubtype] over changing this constant.
 */
class CommandBuilder(
    private val code: Int,
    private val subtype: Int = REQUEST_TYPE,
) {
    private val parts = mutableListOf<String>()

    fun put(key: String, value: Any): CommandBuilder {
        parts += "$key:$value;"
        return this
    }

    fun putRaw(raw: String): CommandBuilder {
        parts += raw
        return this
    }

    fun build(): ByteArray {
        val payload = if (parts.isEmpty()) EMPTY_CONTENT else parts.joinToString("")
        return encodeAscii("1&$code&$subtype&$payload#")
    }
}

/** Default frame builder. Subtype is hard-coded to [REQUEST_TYPE]. */
fun command(code: Int, block: CommandBuilder.() -> Unit = {}): ByteArray =
    CommandBuilder(code).apply(block).build()

/**
 * Subtype-aware frame builder. Looks up the firmware-expected subtype
 * via [Codes.subtypeFor], which currently returns 2 for everything
 * except camera-param setters (subtype 1) and image-format reads
 * (subtype 4). Use this for new code paths that want to honour the
 * RE table; the default [command] helper stays at subtype 2 so all
 * existing tests continue to pass.
 */
fun commandWithSubtype(code: Int, block: CommandBuilder.() -> Unit = {}): ByteArray =
    CommandBuilder(code, Codes.subtypeFor(code)).apply(block).build()
