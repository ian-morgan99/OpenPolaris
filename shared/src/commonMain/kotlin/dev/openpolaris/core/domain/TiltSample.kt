package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.ResponseParser

/**
 * One sample on the tilt push stream (issue #6).
 *
 * On real hardware the mount issues a continuous stream of
 * `SET_TILT_STATE = 538` frames on the same TCP socket as request/response
 * traffic. The tilt envelope carries two fields, `pitch` and `roll`,
 * in degrees. See `docs/PLANNING-2026-08.md` for the captured protocol
 * and the background-reader motivation.
 *
 * [MountSession.tilt] is a `Flow<TiltSample>` that emits every 538 push
 * in arrival order. A `StateFlow<Frame?>` would have conflated the
 * stream and dropped intermediate samples whenever the push rate
 * exceeded the consumer's poll rate — which is the bug this change
 * fixes (see issue #6, "538 push frames lost" reproducer).
 *
 * [timestampMs] is the wall-clock time the reader saw the frame
 * (`System.currentTimeMillis()`); suitable for diagnostic logging and
 * for any future drift analysis an auto-level settling loop may need.
 * It is not the mount's own timestamp — the protocol does not carry one.
 */
data class TiltSample(
    val pitchDeg: Double,
    val rollDeg: Double,
    val timestampMs: Long,
) {
    companion object {
        /**
         * Parse a 538 frame payload. Returns `null` if either `pitch` or
         * `roll` is missing or unparseable; the caller should drop the
         * frame rather than synthesise a sample.
         *
         * The wire format is `pitch:%f;roll:%f;` per the captured
         * protocol. Frames may also carry `state:` or `ret:` — those
         * are intentionally ignored.
         */
        fun fromFrame(f: ResponseParser.Frame): TiltSample? {
            val pitch = f.float("pitch")?.toDouble()
                ?: f.fields["pitch"]?.toDoubleOrNull()
                ?: return null
            val roll = f.float("roll")?.toDouble()
                ?: f.fields["roll"]?.toDoubleOrNull()
                ?: return null
            return TiltSample(pitch, roll, timestampMs = System.currentTimeMillis())
        }
    }
}
