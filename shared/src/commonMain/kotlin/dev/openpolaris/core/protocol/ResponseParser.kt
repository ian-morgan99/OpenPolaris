package dev.openpolaris.core.protocol

/**
 * Parses mount responses. Tolerant by design: unknown keys are ignored, malformed segments
 * are skipped, and values are taken after the FIRST ':' in each segment so values
 * containing colons survive intact.
 */
class ResponseParser {

    /** A parsed response frame. The optional [raw] field carries the wire payload string
     *  (after stripping the envelope markers) for diagnostic / debug purposes. */
    data class Frame(val code: Int, val fields: Map<String, String>, val raw: String? = null) {
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
                // walk back over consecutive digits
                var j = i
                while (j > 0 && text[j - 1].isDigit()) j--
                // i is the first digit of a run
                val atIdx = text.indexOf('@', i)
                // No '@' anywhere ahead: no response envelope in the buffer.
                if (atIdx < 0) return -1
                if (atIdx > i && text.indexOf('#', atIdx) > atIdx) {
                    return i
                }
                // '@' exists but isn't followed by a '#' terminator — keep scanning past it
                // (so we don't reset i to 0 and infinite-loop, which is what the
                // pre-fix `i = atIdx + 1` did when atIdx was -1).
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
        // segs[0] is the "1" prefix when present, or empty for legacy.
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
         *  containing colons survive intact; malformed segments are skipped.
         *
         *  Live-wire tolerations (2026-08-31, captured from gimbal sw:6.0.0.54):
         *    - `-100ret:-1` — gimbal echoes the empty-payload sentinel as a *prefix*
         *      on the next field name when a request returns an error and the original
         *      request had an empty payload. We strip the `-100` prefix.
         *    - `Temp<a509ca361e0000275a>` — gimbal's 525 connect-burst pushes an ID
         *      wrapped in angle brackets with no `:` separator. We parse this as
         *      `name<value>` → `{name: <value>}`.
         */
        fun parseFields(payload: String): Map<String, String> {
            if (payload.isBlank() || payload == EMPTY_CONTENT) return emptyMap()
            val out = mutableMapOf<String, String>()
            for (raw in payload.split(';')) {
                val seg = raw.trim()
                if (seg.isBlank()) continue
                // Bug fix: strip echo '-100' sentinel used as field-name prefix.
                // e.g. `-100ret:-1` → `ret:-1`
                val stripped = if (seg.startsWith(ECHO_SENTINEL_PREFIX)) seg.substring(ECHO_SENTINEL_PREFIX.length) else seg
                val colonIdx = stripped.indexOf(':')
                if (colonIdx > 0) {
                    out[stripped.substring(0, colonIdx).trim()] = stripped.substring(colonIdx + 1).trim()
                    continue
                }
                // Bug fix: parse `name<value>` (525 connect-burst envelope) when there's no colon.
                val ltIdx = stripped.indexOf('<')
                val gtIdx = stripped.lastIndexOf('>')
                if (ltIdx > 0 && gtIdx > ltIdx) {
                    out[stripped.substring(0, ltIdx).trim()] = stripped.substring(ltIdx, gtIdx + 1).trim()
                }
                // Otherwise: malformed segment, skip (existing behaviour).
            }
            return out
        }

        /** Live-discovered quirk: gimbal echoes `-100` as a prefix when a request with
         *  an empty payload returns an error. Wire: `258@-100ret:-1;#`. The original
         *  empty-payload sentinel `-100` ends up glued to the start of the next field. */
        private const val ECHO_SENTINEL_PREFIX = "-100"
    }
}
