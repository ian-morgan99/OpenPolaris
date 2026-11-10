package dev.openpolaris.core.domain

/**
 * Evidence allow-list for a firmware-bound SLOW transport.
 *
 * The record is deliberately independent from the runtime feature flag: a
 * configuration override cannot manufacture firmware, route, stop or recovery
 * evidence. The default record remains unqualified until a named device and
 * test corpus are recorded.
 */
data class SlowMotionCapabilityRecord(
    val firmwareIdentity: String? = null,
    val gimbalIdentity: String? = null,
    val sourceAppIdentity: String? = null,
    val routeQualified: Boolean = false,
    val axisSignsQualified: Boolean = false,
    val rateEnvelopeQualified: Boolean = false,
    val acknowledgementQualified: Boolean = false,
    val watchdogQualified: Boolean = false,
    val stopQualified: Boolean = false,
    val disconnectRecoveryQualified: Boolean = false,
    val limitsQualified: Boolean = false,
) {
    init {
        listOf(firmwareIdentity, gimbalIdentity, sourceAppIdentity).forEach { value ->
            require(value == null || value.isNotBlank()) { "capability identities must not be blank" }
        }
    }

    val liveReady: Boolean
        get() = firmwareIdentity != null &&
            gimbalIdentity != null &&
            sourceAppIdentity != null &&
            routeQualified &&
            axisSignsQualified &&
            rateEnvelopeQualified &&
            acknowledgementQualified &&
            watchdogQualified &&
            stopQualified &&
            disconnectRecoveryQualified &&
            limitsQualified

    val blockers: List<String>
        get() = buildList {
            if (firmwareIdentity == null) add("exact firmware identity")
            if (gimbalIdentity == null) add("gimbal identity")
            if (sourceAppIdentity == null) add("source app/build provenance")
            if (!routeQualified) add("532/533/534 route and units")
            if (!axisSignsQualified) add("axis signs")
            if (!rateEnvelopeQualified) add("measured rate envelope")
            if (!acknowledgementQualified) add("positive acknowledgement")
            if (!watchdogQualified) add("watchdog/lease expiry")
            if (!stopQualified) add("physical STOP")
            if (!disconnectRecoveryQualified) add("disconnect/reconnect recovery")
            if (!limitsQualified) add("hardware-limit boundary")
        }

    companion object {
        val Unqualified = SlowMotionCapabilityRecord()
    }
}

/**
 * Current build-time allow-list. This is intentionally immutable and
 * unqualified; a future release must replace it with a named evidence record
 * rather than turning on a generic runtime switch.
 */
object SlowMotionCapabilities {
    val current: SlowMotionCapabilityRecord = SlowMotionCapabilityRecord.Unqualified
}
