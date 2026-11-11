package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Physical motor axes. These names remain valid after M3/rotation moves. */
enum class RateAxis {
    M1_PAN,
    M2_TILT,
    M3_ROTATION,
}

/** Units exposed by a qualified capability record. */
enum class RateUnits {
    ARCSECONDS_PER_SECOND,
    DEGREES_PER_SECOND,
    DEVICE_UNITS,
}

/** The exact firmware/gimbal identity that qualified a rate contract. */
data class RateCapabilityIdentity(
    val firmwareId: String,
    val gimbalPayloadSha256: String,
    val profileId: String,
) {
    init {
        require(firmwareId.isNotBlank()) { "firmwareId must not be blank" }
        require(gimbalPayloadSha256.isNotBlank()) { "gimbalPayloadSha256 must not be blank" }
        require(profileId.isNotBlank()) { "profileId must not be blank" }
    }
}

/** Inclusive, calibrated range for one physical axis. */
data class RateAxisBounds(
    val minimum: Double,
    val maximum: Double,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite()) { "rate bounds must be finite" }
        require(minimum <= maximum) { "minimum must not exceed maximum" }
    }

    fun contains(value: Double): Boolean = value.isFinite() && value in minimum..maximum
}

/**
 * A rate capability is deliberately opt-in. An unqualified record may be
 * retained for diagnostics, but it cannot enable a controller or expose a
 * live motor adapter.
 */
data class RateAdjustCapability(
    val identity: RateCapabilityIdentity,
    val qualified: Boolean,
    val units: RateUnits,
    val bounds: Map<RateAxis, RateAxisBounds>,
    /** Policy threshold, not a claim that this exact value is hardware-qualified. */
    val minimumSolutionConfidence: Double = 0.6,
    /** Maximum solve age permitted while a correction is active. */
    val maximumSolutionAgeMs: Long = 5_000L,
    /** Maximum absolute field rotation permitted while a correction is active. */
    val maximumSolutionRotationDegrees: Double = 5.0,
) {
    init {
        require(bounds.keys == RateAxis.entries.toSet()) {
            "rate capability must provide bounds for every physical axis"
        }
        require(minimumSolutionConfidence.isFinite() && minimumSolutionConfidence in 0.0..1.0) {
            "minimum solution confidence must be in [0,1]"
        }
        require(maximumSolutionAgeMs > 0L) { "maximum solution age must be positive" }
        require(maximumSolutionRotationDegrees.isFinite() && maximumSolutionRotationDegrees >= 0.0) {
            "maximum solution rotation must be finite and non-negative"
        }
    }

    fun validate(request: RateAdjustRequest): String? {
        if (request.capability != identity) return "request capability does not match the active capability"
        if (request.version <= 0L) return "request version must be positive"
        for (axis in RateAxis.entries) {
            val value = request.target(axis)
            if (!value.isFinite()) return "${axis.name} target must be finite"
            if (!bounds.getValue(axis).contains(value)) {
                return "${axis.name} target $value is outside the qualified range"
            }
        }
        return null
    }
}

/** Context that must remain true while a fine correction is active. */
data class RateAdjustContext(
    val connected: Boolean = false,
    val trackingConfirmed: Boolean = false,
    val halfSpeedEnabled: Boolean = false,
    val exclusiveMotionAvailable: Boolean = false,
    /** Age of the solve that anchors correction, measured at evaluation time. */
    val solutionAgeMs: Long? = null,
    /** Solver confidence in the [0,1] range. */
    val solutionConfidence: Double? = null,
    /** Absolute field rotation from the solve, in degrees. */
    val solutionRotationDegrees: Double? = null,
)

/** A complete, immutable correction request. UI drafts must not be requests. */
data class RateAdjustRequest(
    val baseProfile: TrackingRate,
    val m1Pan: Double,
    val m2Tilt: Double,
    val m3Rotation: Double,
    val version: Long,
    val capability: RateCapabilityIdentity,
) {
    fun target(axis: RateAxis): Double = when (axis) {
        RateAxis.M1_PAN -> m1Pan
        RateAxis.M2_TILT -> m2Tilt
        RateAxis.M3_ROTATION -> m3Rotation
    }
}

/** Result of a command after the transport has supplied its acknowledgement. */
sealed interface RateCommandResult {
    data class Accepted(val version: Long? = null) : RateCommandResult
    data class Rejected(val reason: String) : RateCommandResult
    /** The outcome cannot be established; callers must not retry automatically. */
    data class Unknown(val reason: String) : RateCommandResult
}

/** The only transport seam a future qualified adapter may implement. */
interface RateAdjustTransport {
    suspend fun apply(request: RateAdjustRequest): RateCommandResult
    suspend fun stop(): RateCommandResult
}

sealed interface RateAdjustState {
    data class Unavailable(val reason: String) : RateAdjustState
    data class Ready(val capability: RateAdjustCapability) : RateAdjustState
    data class Applying(val request: RateAdjustRequest) : RateAdjustState
    data class Active(val request: RateAdjustRequest) : RateAdjustState
    data class Stale(val reason: String, val request: RateAdjustRequest?) : RateAdjustState
    data class Faulted(val reason: String, val request: RateAdjustRequest?) : RateAdjustState
}

/**
 * Safe state machine for future fine-rate and SLOW adapters.
 *
 * This class deliberately does not know how to serialize a 532/533/534 or
 * AC-4A command. Until the exact firmware route, units, signs, acknowledgements
 * and stop/watchdog semantics are qualified, production code can only use this
 * boundary with a simulator or a replay transport.
 *
 * Accepted requests are versioned replacements: a newer request may supersede
 * the active target without an intermediate stop, while a delayed older
 * request is rejected without disturbing the active target. A rejected or
 * unknown replacement requires an explicit stop before another apply.
 */
class SafeRateAdjustController(
    private val transport: RateAdjustTransport,
) : RateAdjustController {
    private val mutableState = MutableStateFlow<RateAdjustState>(
        RateAdjustState.Unavailable("Rate control is not qualified for this session"),
    )
    override val state: StateFlow<RateAdjustState> = mutableState.asStateFlow()

    private var capability: RateAdjustCapability? = null
    private var context = RateAdjustContext()
    private var lastVersion = 0L
    /** Set only when an applied command has an unknown/rejected replacement outcome. */
    private var stopRequired = false
    private val commandLock = Mutex()

    override fun setCapability(value: RateAdjustCapability?) {
        val current = mutableState.value
        capability = value
        val request = currentRequest(current)
        if (request != null) {
            stopRequired = true
            mutableState.value = RateAdjustState.Stale(
                "rate capability changed; explicitly stop or reconnect before applying another request",
                request,
            )
        } else {
            mutableState.value = readyOrUnavailable()
        }
    }

    override fun updateContext(value: RateAdjustContext) {
        val current = mutableState.value
        context = value
        val request = currentRequest(current)
        if (request != null && gateReason(capability) != null) {
            stopRequired = true
            mutableState.value = RateAdjustState.Stale(
                "rate context no longer permits correction; explicitly stop or reconnect",
                request,
            )
        } else if (request == null) {
            mutableState.value = readyOrUnavailable()
        }
    }

    override suspend fun apply(request: RateAdjustRequest): RateCommandResult = commandLock.withLock {
        val current = currentRequest(mutableState.value)
        val cap = capability
        val gate = gateReason(cap)
        if (gate != null) {
            mutableState.value = if (current == null) {
                RateAdjustState.Unavailable(gate)
            } else {
                stopRequired = true
                RateAdjustState.Stale("rate context no longer permits correction: $gate", current)
            }
            return@withLock RateCommandResult.Rejected(gate)
        }
        if (request.version <= lastVersion) {
            // A delayed UI event must not replace or fault a newer active
            // request. The currently applied target remains authoritative,
            // even if the delayed draft is now invalid in another way.
            return@withLock RateCommandResult.Rejected("request version is stale")
        }
        val validation = cap!!.validate(request)
        if (validation != null) return@withLock reject(validation, request, current)
        if (stopRequired) {
            return@withLock RateCommandResult.Rejected("an explicit stop or reconnect is required before retrying")
        }

        mutableState.value = RateAdjustState.Applying(request)
        return@withLock try {
            when (val result = transport.apply(request)) {
                is RateCommandResult.Accepted -> {
                    lastVersion = request.version
                    stopRequired = false
                    mutableState.value = RateAdjustState.Active(request)
                    result
                }
                is RateCommandResult.Rejected -> {
                    // If a live target was being replaced, the physical state
                    // is not safe to assume after a rejected replacement.
                    stopRequired = current != null
                    mutableState.value = RateAdjustState.Faulted(result.reason, request)
                    result
                }
                is RateCommandResult.Unknown -> {
                    stopRequired = true
                    mutableState.value = RateAdjustState.Stale(result.reason, request)
                    result
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val reason = "rate transport failed: ${error.message ?: error::class.simpleName ?: "unknown error"}"
            stopRequired = true
            mutableState.value = RateAdjustState.Stale(reason, request)
            RateCommandResult.Unknown(reason)
        }
    }

    override suspend fun stopCorrection(): RateCommandResult = commandLock.withLock {
        stopCorrectionLocked()
    }

    override suspend fun onTrackingStopped() = commandLock.withLock {
        val result = stopCorrectionLocked()
        context = context.copy(trackingConfirmed = false)
        if (result is RateCommandResult.Accepted) mutableState.value = readyOrUnavailable()
    }

    override suspend fun onDisconnected() = commandLock.withLock {
        val result = stopCorrectionLocked()
        context = context.copy(connected = false, trackingConfirmed = false)
        if (result is RateCommandResult.Accepted) mutableState.value = readyOrUnavailable()
    }

    override suspend fun onLeaseExpired() = commandLock.withLock {
        val active = currentRequest(mutableState.value)
        if (active != null) {
            val result = stopAndSettle(active)
            if (result !is RateCommandResult.Accepted) return@withLock
        }
        stopRequired = true
        mutableState.value = RateAdjustState.Stale("rate-control lease expired; correction was stopped", active)
    }

    private suspend fun stopCorrectionLocked(): RateCommandResult {
        val active = currentRequest(mutableState.value)
        if (active == null) {
            stopRequired = false
            mutableState.value = readyOrUnavailable()
            return RateCommandResult.Accepted()
        }
        return stopAndSettle(active)
    }

    private suspend fun stopAndSettle(request: RateAdjustRequest): RateCommandResult = try {
        when (val result = transport.stop()) {
            is RateCommandResult.Accepted -> {
                stopRequired = false
                mutableState.value = readyOrUnavailable()
                result
            }
            is RateCommandResult.Rejected -> {
                stopRequired = true
                mutableState.value = RateAdjustState.Faulted(result.reason, request)
                result
            }
            is RateCommandResult.Unknown -> {
                stopRequired = true
                mutableState.value = RateAdjustState.Stale(result.reason, request)
                result
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val reason = "rate stop failed: ${error.message ?: error::class.simpleName ?: "unknown error"}"
        stopRequired = true
        mutableState.value = RateAdjustState.Stale(reason, request)
        RateCommandResult.Unknown(reason)
    }

    private fun reject(
        reason: String,
        attempted: RateAdjustRequest,
        current: RateAdjustRequest?,
    ): RateCommandResult {
        // An invalid replacement was never sent. Preserve the last request
        // whose physical outcome is still possible, rather than displaying an
        // unsent draft as the active target. A first invalid request has no
        // physical target and therefore remains recoverable without a stop.
        stopRequired = current != null
        mutableState.value = RateAdjustState.Faulted(reason, current ?: attempted)
        return RateCommandResult.Rejected(reason)
    }

    private fun currentRequest(state: RateAdjustState): RateAdjustRequest? = when (state) {
        is RateAdjustState.Applying -> state.request
        is RateAdjustState.Active -> state.request
        is RateAdjustState.Stale -> state.request
        is RateAdjustState.Faulted -> state.request
        else -> null
    }

    private fun readyOrUnavailable(): RateAdjustState {
        val cap = capability
        val reason = gateReason(cap)
        return if (reason == null) RateAdjustState.Ready(cap!!) else RateAdjustState.Unavailable(reason)
    }

    private fun gateReason(cap: RateAdjustCapability?): String? {
        val solutionAgeMs = context.solutionAgeMs
        val solutionConfidence = context.solutionConfidence
        val solutionRotationDegrees = context.solutionRotationDegrees
        return when {
            cap == null -> "Rate control is not qualified for this session"
            !cap.qualified -> "Rate control capability is unqualified for this firmware"
            !context.connected -> "Connect to the mount before using fine correction"
            !context.trackingConfirmed -> "Tracking must be confirmed before using fine correction"
            context.halfSpeedEnabled -> "Fine correction is unavailable while half speed is enabled"
            !context.exclusiveMotionAvailable -> "Another motion owner currently controls the mount"
            solutionAgeMs == null -> "A fresh plate solve is required before fine correction"
            solutionAgeMs < 0L -> "Plate-solve age is invalid"
            solutionAgeMs > cap.maximumSolutionAgeMs ->
                "Plate solve is stale (${solutionAgeMs} ms > ${cap.maximumSolutionAgeMs} ms)"
            solutionConfidence == null -> "Plate-solve confidence is unknown"
            !solutionConfidence.isFinite() -> "Plate-solve confidence is invalid"
            solutionConfidence < cap.minimumSolutionConfidence ->
                "Plate-solve confidence is below ${cap.minimumSolutionConfidence}"
            solutionRotationDegrees == null -> "Plate-solve field rotation is unknown"
            !solutionRotationDegrees.isFinite() -> "Plate-solve field rotation is invalid"
            kotlin.math.abs(solutionRotationDegrees) > cap.maximumSolutionRotationDegrees ->
                "Plate-solve field rotation exceeds ${cap.maximumSolutionRotationDegrees} degrees"
            else -> null
        }
    }
}

interface RateAdjustController {
    val state: StateFlow<RateAdjustState>
    fun setCapability(value: RateAdjustCapability?)
    fun updateContext(value: RateAdjustContext)
    suspend fun apply(request: RateAdjustRequest): RateCommandResult
    suspend fun stopCorrection(): RateCommandResult
    suspend fun onTrackingStopped()
    suspend fun onDisconnected()
    suspend fun onLeaseExpired()
}
