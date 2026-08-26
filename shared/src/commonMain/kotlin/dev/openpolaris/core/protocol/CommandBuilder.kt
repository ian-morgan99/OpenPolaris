package dev.openpolaris.core.protocol

/**
 * Builds wire-format request frames: `1&<code>&<type>&<payload>#`.
 * Payload is a `key:value;` string, or [EMPTY_CONTENT] when absent.
 */
class CommandBuilder(private val code: Int) {
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
        return encodeAscii("1&$code&$REQUEST_TYPE&$payload#")
    }
}

fun command(code: Int, block: CommandBuilder.() -> Unit = {}): ByteArray =
    CommandBuilder(code).apply(block).build()
