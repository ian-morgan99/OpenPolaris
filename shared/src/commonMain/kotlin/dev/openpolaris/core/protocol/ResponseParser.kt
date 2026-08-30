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
     */
    fun parse(buffer: ByteArray): Pair<List<Frame>, Int> {
        val text = decodeAscii(buffer)
        val frames = mutableListOf<Frame>()
        var searchFrom = 0
        var consumed = 0
        while (true) {
            val start = text.indexOf("1&", searchFrom)
            if (start < 0) break
            val end = text.indexOf('#', start)
            if (end < 0) break
            parseFrame(text.substring(start, end))?.let frames@{
                frames += it
                consumed = end + 1
                searchFrom = end + 1
                return@frames
            }
            // Unparseable candidate: skip past its start to avoid an infinite loop.
            searchFrom = start + 2
        }
        return frames to consumed
    }

    /** Parse one frame body without the leading '1' or trailing '#'. */
    fun parseFrame(body: String): Frame? {
        // body: "&<code>&<type>&<payload>"
        val segs = body.split('&')
        if (segs.size < 4) return null
        val code = segs[1].trim().toIntOrNull() ?: return null
        val payload = segs.drop(3).joinToString("&")
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
