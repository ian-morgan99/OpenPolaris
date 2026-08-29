package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.CommandTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Astro helper settings (DITHER, SETTLING_TIME, LIMITS).
 *
 * The mount exposes three dials that guide the firmware's tracking
 * behaviour during a long exposure:
 *
 *  * **Dithering** (539/540) — small random RA nudges between subs to
 *    average out pixel-scale noise. Benro default is ON.
 *  * **Settling time** (543/544) — how long the firmware waits for the
 *    mount to stabilise after a goto before resuming tracking. Seconds.
 *  * **Limits** (541/542) — when ON, the mount refuses gotos outside its
 *    mechanical envelope. OFF allows the firmware to push past the stops
 *    (e.g. for testing). The LIMITS wire format is a best-effort guess
 *    mirrored from the TILT pattern; verify against real hardware before
 *    marking verified.
 *
 * No persistent state: each setter sends the value and updates the
 * local cache to match. Callers can re-read on connect with [refreshAll].
 */
class HelpersController(private val session: MountSession) {

    private val _ditherEnabled = MutableStateFlow<Boolean?>(null)
    /** Last-known dithering flag; null while unknown. */
    val ditherEnabled: StateFlow<Boolean?> = _ditherEnabled.asStateFlow()

    private val _settlingSeconds = MutableStateFlow<Int?>(null)
    /** Last-known settling time in seconds; null while unknown. */
    val settlingSeconds: StateFlow<Int?> = _settlingSeconds.asStateFlow()

    private val _limitsEnabled = MutableStateFlow<Boolean?>(null)
    /** Last-known limits flag; null while unknown. UNVERIFIED on real mount. */
    val limitsEnabled: StateFlow<Boolean?> = _limitsEnabled.asStateFlow()

    /** No frame subscription needed — none of the helper codes push unsolicited updates. */
    fun start(@Suppress("UNUSED_PARAMETER") scope: CoroutineScope) = Unit

    fun stop() = Unit

    /** Refresh all three helpers from the mount. Failures are isolated per call. */
    suspend fun refreshAll() {
        refreshDither()
        refreshSettling()
        refreshLimits()
    }

    /** Refresh dithering (539). */
    suspend fun refreshDither(): Boolean? =
        (read(CommandTable.DITHER_GET)?.let { it != 0 }).also { _ditherEnabled.value = it }

    /** Toggle dithering (540). */
    suspend fun setDither(on: Boolean) {
        session.send(CommandTable.DITHER_SET.code, CommandTable.DITHER_SET.payload(on))
        _ditherEnabled.value = on
    }

    /** Refresh settling time (543). */
    suspend fun refreshSettling(): Int? =
        read(CommandTable.SETTLING_TIME_GET).also { _settlingSeconds.value = it }

    /** Set settling time in seconds (544). Clamped to non-negative. */
    suspend fun setSettling(seconds: Int) {
        val v = if (seconds < 0) 0 else seconds
        session.send(CommandTable.SETTLING_TIME_SET.code, CommandTable.SETTLING_TIME_SET.payload(v))
        _settlingSeconds.value = v
    }

    /** Refresh limits flag (541). UNVERIFIED on real mount. */
    suspend fun refreshLimits(): Boolean? =
        read(CommandTable.LIMITS_GET).also { _limitsEnabled.value = it }

    /** Toggle limits (542). UNVERIFIED on real mount. */
    suspend fun setLimits(on: Boolean) {
        session.send(CommandTable.LIMITS_SET.code, CommandTable.LIMITS_SET.payload(on))
        _limitsEnabled.value = on
    }

    private suspend fun <T> read(d: CommandTable.Descriptor<T?>): T? = when (
        val r = session.request(d.code) { f -> d.parse?.invoke(f) }
    ) {
        is MountSession.CmdResult.Ok -> r.value
        else -> null
    }
}
