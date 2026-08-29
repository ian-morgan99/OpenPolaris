package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.TiltCodec
import kotlinx.coroutines.flow.first

/**
 * One sample on the tilt push stream.
 *
 * On real hardware, the mount issues a continuous stream of `SET_TILT_STATE = 538`
 * frames on the same TCP socket as request/response traffic. A single
 * `StateFlow<Frame?>` (the existing [MountSession.frames]) only retains the
 * most-recent frame, so if the push rate exceeds the consumer's poll rate
 * intermediate samples are lost — a production gap flagged in
 * PLAN-CRITICAL-REVIEW §F and GitHub issue #6.
 *
 * [MountSession.tilt] is the correct shape for the push stream: a
 * `Flow<TiltSample>` backed by a buffered channel so every 538 push reaches
 * a downstream subscriber in arrival order.
 *
 * The fields mirror the on-wire payload `pitch:%f;roll:%f;` decoded by
 * [TiltCodec] (degrees). [timestamp] is the time the reader saw the
 * frame, in milliseconds since the system monotonic clock epoch
 * (`kotlin.system.measureTimeMillis` / `TimeSource.Monotonic.markNow`),
 * suitable for the AHRS settling-window predicate's drift analysis.
 */
data class TiltSample(
    val pitchDeg: Double,
    val rollDeg: Double,
    val timestampMs: Long,
)

/**
 * Pluggable source of tilt samples for [AutoLevelController]'s settling
 * loop. Production callers wire a [MountSessionTiltSampleSource] (which
 * subscribes to [MountSession.tilt]); tests pass a queue-based fake so
 * they can deliver samples deterministically without spinning up a real
 * socket reader (PLAN-CRITICAL-REVIEW §F).
 *
 * [next] returns the next sample, suspending until one is available, or
 * `null` if the source is exhausted / the session has disconnected.
 */
fun interface TiltSampleSource {
    suspend fun next(): TiltSample?
}

/**
 * Production [TiltSampleSource] backed by a [MountSession]'s [MountSession.tilt]
 * push stream. Each [next] call awaits the next 538 sample and maps it from the
 * wire-shape [TiltSample] (pitchDeg/rollDeg as Double) to the
 * [AutoLevelController.Tilt] the settling loop expects.
 *
 * The source is single-use: every call to [next] re-subscribes to
 * [MountSession.tilt] with `Flow.first`. That matches how [AutoLevelController]
 * uses its `sampleSource` (one call per settling window) and is safe because
 * the source is meant to be created once per `connect()` cycle and discarded
 * on `disconnect()`.
 *
 * The mapping is a structural copy of the two degree fields. We do NOT route
 * through [TiltCodec] here: the codec works on parsed `ResponseParser.Frame`s
 * and we already have a decoded [TiltSample] in hand.
 *
 * Returns `null` (matching the [TiltSampleSource] contract for "exhausted")
 * if the underlying flow closes without emitting. On a healthy session this
 * never happens while connected; on disconnect the consumer's scope cancels
 * the pending `first()` call, so we never actually surface the null.
 */
class MountSessionTiltSampleSource(
    private val session: MountSession,
) : TiltSampleSource {
    override suspend fun next(): TiltSample? = session.tilt.first()
}
