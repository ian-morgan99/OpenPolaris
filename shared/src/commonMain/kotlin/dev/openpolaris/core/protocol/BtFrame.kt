package dev.openpolaris.core.protocol

/**
 * Bluetooth-side frame codec for the Polaris gimbal.
 *
 * The BT link is used purely as a **wake pulse** (see [dev.openpolaris.core.net.BluetoothProbe]);
 * the BT payload format is only relevant to the official Benro app's pre-WiFi handshake
 * (GATT services fff0/fff1/fff2). It is **not** the same wire format as the WiFi TCP control
 * channel — see [CommandBuilder] / [ResponseParser] for that.
 *
 * BT frame envelope observed in live captures (PLANNING-2026-08.md §Bluetooth):
 *
 * ```
 *   code:<N>;#<key:val;…>#
 *   ↑    ↑   ↑   ↑      ↑
 *   │    │   │   │      └ terminating '#'
 *   │    │   │   └ key:value pairs, ';' separated (semicolon-then-# terminator)
 *   │    │   └ payload opens with '#', closes with '#'
 *   │    └ colon separator
 *   └ literal "code:" prefix
 * ```
 *
 * Example envelopes seen in live captures:
 *
 * ```
 *   code:1;#        # literal ack of the wake pulse (no payload, just envelope echo)
 *   code:1;#sn:0000;#
 *   code:524;#state:0;#
 * ```
 *
 * Note the **double-`#` envelope**: the body itself is `#…#`, so an empty payload
 * produces `code:1;#` (the inner `#` is both the open and the close).
 *
 * The codes seen on the BT link are a **subset** of the WiFi codes (258/259/260/261/262/263
 * camera params, 513–524 motion/control) plus a small BT-only handshake set (1–5). Codes
 * 1–5 are not yet captured-wire pinned — names below are placeholders until a pcap
 * of the live BT handshake confirms them.
 *
 * GATT UUIDs (PLANNING-2026-08.md):
 *
 * ```
 *   service: 0000fff0-0000-1000-8000-00805f9b34fb
 *   tx:      0000fff1-0000-1000-8000-00805f9b34fb  (host → gimbal)
 *   rx:      0000fff2-0000-1000-8000-00805f9b34fb  (gimbal → host)
 *   MTU requested: 512
 * ```
 */
object BtFrame {

    // ---- GATT UUIDs (PLANNING-2026-08.md §Bluetooth) -------------------------
    const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    const val TX_CHARACTERISTIC_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
    const val RX_CHARACTERISTIC_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
    /** MTU to request on MTU-exchange; 512 is the stock-app value. */
    const val PREFERRED_MTU = 512

    // ---- BT-only handshake codes (1..5) ---------------------------------------
    // Names below are tentative placeholders — exact semantics on the wire are
    // **not yet captured** — treat as provisional until a pcap of the live
    // BT handshake confirms them.
    const val BT_HELLO = 1                  // host → gimbal: open link, request serial
    const val BT_HELLO_ACK = 2              // gimbal → host: serial number + state
    const val BT_TOKEN_EXCHANGE = 3         // host → gimbal: app token (APP_TOKEN=821 equivalent)
    const val BT_TOKEN_EXCHANGE_ACK = 4     // gimbal → host: ack
    const val BT_WIFI_TRIGGER = 5           // host → gimbal: turn on Wi-Fi AP (or close link, see note)

    // ---- BT-side codes shared with WiFi (for documentation only) --------------
    // These are NOT re-defined here — use the existing [Codes] constants.
    //   257..263  →  CAM_* in [Codes]   (258=CAM_GET_ISO … 263=CAM_SET_FNUM)
    //   513..524  →  motion/control in [Codes]
    //
    // Note: BT scan-name filter per RE: "polaris_", "theta_".

    /**
     * Encode a BT frame.
     *
     * @param code the spCode carried in the body
     * @param fields optional key:value pairs; may be empty
     * @return ASCII bytes ready to write to the GATT TX characteristic
     */
    fun build(code: Int, fields: Map<String, String> = emptyMap()): ByteArray {
        // Format per RE capture:
        //   code:<N>;#              when no fields   (the single '#' is the (collapsed) payload envelope)
        //   code:<N>;#<k>:<v>;…#    when fields present (each field ends with ';')
        //
        // The "payload envelope" is `#…#`. When the payload is empty, the two '#' collapse
        // to a single one — the same character is both the open and the close.
        val body = if (fields.isEmpty()) "" else fields.entries.joinToString("") { (k, v) -> "$k:$v;" }
        return if (body.isEmpty()) {
            encodeAscii("code:$code;#")
        } else {
            encodeAscii("code:$code;#${body}#")
        }
    }

    /**
     * Parse a BT frame.
     *
     * Grammar (per RE capture):
     *
     * ```
     *   code:N;              <- prefix
     *   #                    <- open of payload envelope (also close when payload empty)
     *   k1:v1;k2:v2;…        <- payload (fields, each terminated with ';')
     *   #?                   <- close of payload envelope (absent when payload empty)
     * ```
     *
     * @return null if [bytes] does not match the grammar
     */
    fun parse(bytes: ByteArray): Parsed? {
        val text = decodeAscii(bytes).trim()
        if (!text.startsWith("code:")) return null
        // After "code:" we expect "<N>;#…"
        val afterPrefix = text.substring("code:".length)
        val semi = afterPrefix.indexOf(';')
        if (semi < 0) return null
        val code = afterPrefix.substring(0, semi).trim().toIntOrNull() ?: return null
        // Now afterPrefix[semi] = ';', afterPrefix[semi+1] must be '#'
        if (semi + 1 >= afterPrefix.length || afterPrefix[semi + 1] != '#') return null
        val rest = afterPrefix.substring(semi + 2)
        // rest is either "" (== no body, no close '#') or "<body>#" or "<body>" (malformed)
        val body = if (rest.isEmpty()) {
            ""
        } else if (rest.endsWith("#")) {
            rest.substring(0, rest.length - 1)
        } else {
            return null
        }
        val fields = if (body.isEmpty()) emptyMap() else parseFields(body)
        return Parsed(code, fields, body)
    }

    data class Parsed(
        val code: Int,
        val fields: Map<String, String>,
        /** Raw body text between the inner `#` markers; null for empty body. */
        val raw: String? = null,
    ) {
        operator fun get(key: String): String? = fields[key]
        fun int(key: String): Int? = fields[key]?.trim()?.toIntOrNull()
        fun float(key: String): Float? = fields[key]?.trim()?.toFloatOrNull()
    }

    private fun parseFields(payload: String): Map<String, String> {
        if (payload.isBlank()) return emptyMap()
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
