package dev.openpolaris.core.protocol

/**
 * Parses mount responses. Tolerant by design: unknown keys are ignored, malformed segments
 * are skipped, and values are taken after the FIRST ':' in each segment so values
 * containing colons (e.g. ISO-8601 timestamps) survive intact.
 */
class ResponseParser {

    /** A parsed response frame. */
    data class Frame(
        val code: Int,
        val fields: Map<String, String>,
        /**
         * Raw payload text (the substring between the type segment and the
         * terminating `#`, e.g. `count:2;id:0;state:0;name:Slew;id:1;...;`).
         *
         * Carried alongside [fields] so list-of-records parsers (e.g. OMS
         * task list) can recover individual records that the field map
         * collapses. May be null if the frame was constructed without a
         * raw payload (e.g. in tests).
         */
        val raw: String? = null,
    ) {
        operator fun get(key: String): String? = fields[key]
        fun int(key: String): Int? = fields[key]?.trim()?.toIntOrNull()
        fun float(key: String): Float? = fields[key]?.trim()?.toFloatOrNull()
    }

    /**
     * Extract and parse all complete frames from a byte buffer.
     * Returns parsed frames and the number of bytes consumed (end of last complete frame),
     * so callers can keep any partial trailing frame for the next read.
     *
     * Two envelope shapes are accepted:
     *  - Request frame (app→gimbal style): `1&<code>&<type>&<payload>#` — 4 ampersand-separated segments.
     *  - Response frame (gimbal→app, captured live 2026-08-30): `<code>@<k1>:<v1>;<k2>:<v2>;...;#`.
     */
    fun parse(buffer: ByteArray): Pair<List<Frame>, Int> {
        val text = decodeAscii(buffer)
        val frames = mutableListOf<Frame>()
        var searchFrom = 0
        var consumed = 0
        while (searchFrom < text.length) {
            val next = nextFrameStart(text, searchFrom) ?: break
            val start = next.first
            val end = text.indexOf('#', start)
            if (end < 0) break
            val fr = parseFrame(text.substring(start, end + 1))
            if (fr != null) {
                frames += fr
                consumed = end + 1
                searchFrom = end + 1
            } else {
                // Unparseable candidate: skip past the matched opener to avoid an infinite loop.
                searchFrom = start + next.second
            }
        }
        return frames to consumed
    }

    /**
     * Find the start of the next frame candidate at or after [from].
     * Returns (index, bytes-to-skip-if-unparseable), or null if neither envelope is present.
     */
    private fun nextFrameStart(text: String, from: Int): Pair<Int, Int>? {
        val reqIdx = text.indexOf("1&", from)
        val respIdx = findResponseStart(text, from)
        return when {
            reqIdx < 0 && respIdx < 0 -> null
            reqIdx < 0 -> respIdx!! to 1 // skip past the digits that looked like a start
            respIdx < 0 -> reqIdx to 2
            else -> if (reqIdx <= respIdx) reqIdx to 2 else respIdx to 1
        }
    }

    /**
     * Scan for a position where a digit run is immediately followed by `@`.
     * That's the opener of the response envelope `<code>@...;#`. Returns the index
     * of the first digit, or -1 if none found.
     */
    private fun findResponseStart(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val c = text[i]
            if (c.isDigit()) {
                val atIdx = text.indexOf('@', i)
                if (atIdx > i && text.indexOf('#', atIdx) > atIdx) {
                    return i
                }
                i = atIdx + 1
            } else {
                i++
            }
        }
        return -1
    }

    /**
     * Parse one frame. Accepts the leading type marker (e.g. `1&`) for request frames
     * and the trailing `#` for both envelopes.
     */
    fun parseFrame(body: String): Frame? {
        // Strip trailing '#' if present.
        val trimmed = if (body.endsWith('#')) body.dropLast(1) else body
        return parseRequestFrame(trimmed) ?: parseResponseFrame(trimmed)
    }

    /** `1&<code>&<type>&<payload>` — request shape (captured live 2026-08-30). */
    private fun parseRequestFrame(body: String): Frame? {
        // Body may start with "1" (app→gimbal style captured live) or "&" (legacy shape).
        val segs = body.split('&')
        if (segs.size < 4) return null
        val code = segs[1].trim().toIntOrNull() ?: return null
        val payload = segs.drop(3).joinToString("&")
        return Frame(code, parseFields(payload), raw = payload)
    }

    /** `<code>@<k1>:<v1>;<k2>:<v2>;...;` — response shape captured live 2026-08-30. */
    private fun parseResponseFrame(body: String): Frame? {
        val atIdx = body.indexOf('@')
        if (atIdx <= 0) return null
        val code = body.substring(0, atIdx).trim().toIntOrNull() ?: return null
        val payload = body.substring(atIdx + 1)
        return Frame(code, parseFields(payload), raw = payload)
    }

    companion object {
        /** Split `a:1;b:2;` into a map. Values are taken after the FIRST ':' so values
         *  containing colons survive intact; malformed segments are skipped. */
        fun parseFields(payload: String): Map<String, String> {
            if (payload.isBlank() || payload == EMPTY_CONTENT) return emptyMap()
            val out = mutableMapOf<String, String>()
            for (seg in payload.split(';')) {
                if (seg.isBlank()) continue
                val i = seg.indexOf(':')
                if (i <= 0) continue
                out[seg.substring(0, i).trim()] = seg.substring(i + 1).trim()
            }
            return out
        }
    }
}
