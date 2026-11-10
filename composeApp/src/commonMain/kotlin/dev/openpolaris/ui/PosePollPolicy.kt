package dev.openpolaris.ui

/**
 * Cadence policy for the low-cost 284/517 pose reads.
 *
 * An idle mount does not need a new position pair every second: two requests
 * every two seconds keep the UI fresh enough while reducing camera/PTP
 * contention to about one request per second. Tracking or any owned motion
 * returns to the 1 Hz cadence so the operator sees movement promptly.
 */
internal object PosePollPolicy {
    const val ACTIVE_DELAY_MS: Long = 1_000L
    const val IDLE_DELAY_MS: Long = 2_000L

    fun delayMs(
        tracking: Boolean,
        motionOwned: Boolean,
        slewInProgress: Boolean,
    ): Long = if (tracking || motionOwned || slewInProgress) {
        ACTIVE_DELAY_MS
    } else {
        IDLE_DELAY_MS
    }
}
