package dev.openpolaris.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Result from a future firmware-bound SLOW command adapter. */
sealed interface SlowMotionTransportResult {
    data object Accepted : SlowMotionTransportResult
    data class Rejected(val reason: String) : SlowMotionTransportResult
    /** The command outcome is unknown; the controller never retries it. */
    data class Unknown(val reason: String) : SlowMotionTransportResult
}

enum class SlowMotionTelemetryEventKind {
    STARTED,
    COMMAND_ATTEMPTED,
    POSITIVE_ACKNOWLEDGEMENT,
    REJECTED,
    UNKNOWN,
    STOP_REQUESTED,
    STOP_CONFIRMED,
    STOP_REJECTED,
    STOP_UNKNOWN,
    RECOVERED,
}

data class SlowMotionTelemetryEvent(
    val sequence: Long,
    val atMs: Long? = null,
    val kind: SlowMotionTelemetryEventKind,
    val command: SlowScheduleCommand? = null,
    val detail: String? = null,
)

/**
 * The only wire-independent transport seam for SLOW. Implementations must
 * provide an exact, evidence-backed command mapping; this interface does not
 * serialize 532/533/534 or AC 4A and cannot make an unqualified adapter safe.
 */
interface SlowMotionTransport {
    suspend fun apply(command: SlowScheduleCommand): SlowMotionTransportResult
    suspend fun stop(reason: MotionStopReason): MotionStopResult
}

data class SlowMotionLease(
    val token: Long,
    /** Shared physical-motion lease, when this controller participates in arbitration. */
    val supervisorLease: MotionLease? = null,
)

sealed interface SlowMotionAcquireResult {
    data class Granted(val lease: SlowMotionLease) : SlowMotionAcquireResult
    /** [lease] is present for a local competing hold and null for another owner. */
    data class Busy(val lease: SlowMotionLease? = null, val owner: MotionOwner? = null) : SlowMotionAcquireResult
    data class Blocked(val reason: String) : SlowMotionAcquireResult
}

sealed interface SlowMotionActionResult {
    data object Started : SlowMotionActionResult
    data object Renewed : SlowMotionActionResult
    data object NotDue : SlowMotionActionResult
    data object Stopped : SlowMotionActionResult
    data object Rejected : SlowMotionActionResult
    data class Applied(val commandCount: Int) : SlowMotionActionResult
    data class Unknown(val reason: String) : SlowMotionActionResult
}

/**
 * Bounded, monotonic telemetry for the last scheduler point.
 *
 * The timestamp is the controller clock supplied to [SlowMotionController.tick],
 * not wall-clock time. [lastPositiveAcknowledgementAtMs] is only advanced by
 * an explicit [SlowMotionTransportResult.Accepted] result; an unknown or
 * rejected result never looks like a successful movement acknowledgement.
 */
data class SlowMotionTelemetry(
    val lastCommand: SlowScheduleCommand? = null,
    val lastCommandAtMs: Long? = null,
    val lastPositiveAcknowledgementAtMs: Long? = null,
    val lastOutcome: SlowMotionTransportResult? = null,
    val events: List<SlowMotionTelemetryEvent> = emptyList(),
)

sealed interface SlowMotionState {
    data object Idle : SlowMotionState
    data class Armed(val lease: SlowMotionLease) : SlowMotionState
    data class Holding(
        val lease: SlowMotionLease,
        val plan: SlowMotionPlan,
        val lastRenewalMs: Long,
    ) : SlowMotionState
    data class Faulted(val reason: String, val lease: SlowMotionLease?) : SlowMotionState
}

/**
 * Transport-neutral SLOW owner.
 *
 * It combines the measured/reference planner and clock scheduler with an
 * exclusive lease and an explicit dead-man renewal window. It is suitable for
 * a simulator or replay adapter today. A production adapter still needs
 * firmware-specific routing, signs, acknowledgement, watchdog and physical
 * stop evidence before it can be installed.
 */
class SlowMotionController(
    private val transport: SlowMotionTransport,
    private val planner: SlowMotionPlanner = SlowMotionPlanner(SlowMotionCalibration.REFERENCE),
    private val scheduler: SlowMotionScheduler = SlowMotionScheduler(),
    private val leaseWindowMs: Long = 400L,
    private val motionSupervisor: MotionSupervisor? = null,
) {
    init {
        require(leaseWindowMs > 0L)
    }

    private val lock = Mutex()
    private val mutableState = MutableStateFlow<SlowMotionState>(SlowMotionState.Idle)
    val state: StateFlow<SlowMotionState> = mutableState.asStateFlow()
    private val mutableTelemetry = MutableStateFlow(SlowMotionTelemetry())
    /** Last attempted point and explicit positive acknowledgement evidence. */
    val telemetry: StateFlow<SlowMotionTelemetry> = mutableTelemetry.asStateFlow()
    private var nextToken = 1L
    private var nextTelemetrySequence = 1L

    suspend fun acquire(): SlowMotionAcquireResult = lock.withLock {
        when (val current = mutableState.value) {
            SlowMotionState.Idle -> {
                val supervisorLease = when (val supervisor = motionSupervisor) {
                    null -> null
                    else -> when (val acquired = supervisor.acquire(MotionOwner.SLOW_MOVE)) {
                        is MotionAcquireResult.Granted -> acquired.lease
                        is MotionAcquireResult.Busy -> {
                            return@withLock SlowMotionAcquireResult.Busy(owner = acquired.active.owner)
                        }
                        is MotionAcquireResult.Blocked -> {
                            return@withLock SlowMotionAcquireResult.Blocked(acquired.reason)
                        }
                    }
                }
                val lease = SlowMotionLease(nextToken++, supervisorLease)
                mutableState.value = SlowMotionState.Armed(lease)
                SlowMotionAcquireResult.Granted(lease)
            }
            is SlowMotionState.Armed -> SlowMotionAcquireResult.Busy(current.lease)
            is SlowMotionState.Holding -> SlowMotionAcquireResult.Busy(current.lease)
            is SlowMotionState.Faulted -> SlowMotionAcquireResult.Blocked(current.reason)
        }
    }

    suspend fun start(
        lease: SlowMotionLease,
        targets: Map<RateAxis, SlowMotionTarget>,
        nowMs: Long,
    ): SlowMotionActionResult = lock.withLock {
        requireTime(nowMs)
        if ((mutableState.value as? SlowMotionState.Armed)?.lease != lease) {
            return@withLock SlowMotionActionResult.Rejected
        }
        val plan = try {
            planner.plan(targets)
        } catch (_: IllegalArgumentException) {
            return@withLock SlowMotionActionResult.Rejected
        }
        scheduler.setPlan(plan, nowMs)
        mutableState.value = SlowMotionState.Holding(lease, plan, nowMs)
        recordTelemetryEvent(SlowMotionTelemetryEventKind.STARTED, atMs = nowMs)
        SlowMotionActionResult.Started
    }

    /**
     * Renew the dead-man lease without emitting a command. The host should
     * call this only while the physical/UI hold is still asserted.
     */
    suspend fun renew(lease: SlowMotionLease, nowMs: Long): SlowMotionActionResult = lock.withLock {
        requireTime(nowMs)
        val holding = mutableState.value as? SlowMotionState.Holding
        if (holding?.lease != lease) return@withLock SlowMotionActionResult.Rejected
        if (nowMs - holding.lastRenewalMs >= leaseWindowMs) {
            return@withLock stopLocked(lease, MotionStopReason.LEASE_EXPIRED)
        }
        mutableState.value = holding.copy(lastRenewalMs = nowMs)
        SlowMotionActionResult.Renewed
    }

    /**
     * Emit due logical scheduler commands. Renewal is intentionally separate:
     * a scheduler tick must not keep a dead-man lease alive after input loss.
     */
    suspend fun tick(lease: SlowMotionLease, nowMs: Long): SlowMotionActionResult = lock.withLock {
        requireTime(nowMs)
        val holding = mutableState.value as? SlowMotionState.Holding
        if (holding?.lease != lease) return@withLock SlowMotionActionResult.Rejected
        if (nowMs - holding.lastRenewalMs >= leaseWindowMs) {
            return@withLock stopLocked(lease, MotionStopReason.LEASE_EXPIRED)
        }

        val commands = scheduler.tick(nowMs)
        if (commands.isEmpty()) return@withLock SlowMotionActionResult.NotDue
        for (command in commands) {
            // An initial idle-axis STOP without a retained point is a logical
            // no-op. Do not present it as a positive wire acknowledgement.
            val logicalNoOp = command.kind == SlowScheduleKind.STOP && command.point == null
            if (!logicalNoOp) {
                mutableTelemetry.value = mutableTelemetry.value.copy(
                    lastCommand = command,
                    lastCommandAtMs = nowMs,
                    lastOutcome = null,
                )
                recordTelemetryEvent(
                    SlowMotionTelemetryEventKind.COMMAND_ATTEMPTED,
                    atMs = nowMs,
                    command = command,
                )
            }
            when (val result = runCatching { transport.apply(command) }.getOrElse {
                SlowMotionTransportResult.Unknown(
                    "slow-motion command failed: ${it.message ?: it::class.simpleName ?: "unknown error"}",
                )
            }) {
                SlowMotionTransportResult.Accepted -> {
                    if (!logicalNoOp) {
                        mutableTelemetry.value = mutableTelemetry.value.copy(
                            lastOutcome = result,
                            lastPositiveAcknowledgementAtMs = nowMs,
                        )
                        recordTelemetryEvent(
                            SlowMotionTelemetryEventKind.POSITIVE_ACKNOWLEDGEMENT,
                            atMs = nowMs,
                            command = command,
                        )
                    }
                }
                is SlowMotionTransportResult.Rejected -> {
                    if (!logicalNoOp) {
                        mutableTelemetry.value = mutableTelemetry.value.copy(lastOutcome = result)
                        recordTelemetryEvent(
                            SlowMotionTelemetryEventKind.REJECTED,
                            atMs = nowMs,
                            command = command,
                            detail = result.reason,
                        )
                    }
                    mutableState.value = SlowMotionState.Faulted(result.reason, lease)
                    return@withLock SlowMotionActionResult.Rejected
                }
                is SlowMotionTransportResult.Unknown -> {
                    if (!logicalNoOp) {
                        mutableTelemetry.value = mutableTelemetry.value.copy(lastOutcome = result)
                        recordTelemetryEvent(
                            SlowMotionTelemetryEventKind.UNKNOWN,
                            atMs = nowMs,
                            command = command,
                            detail = result.reason,
                        )
                    }
                    mutableState.value = SlowMotionState.Faulted(result.reason, lease)
                    return@withLock SlowMotionActionResult.Unknown(result.reason)
                }
            }
        }
        SlowMotionActionResult.Applied(commands.size)
    }

    suspend fun release(
        lease: SlowMotionLease,
        reason: MotionStopReason = MotionStopReason.RELEASE,
    ): SlowMotionActionResult = lock.withLock {
        when (val current = mutableState.value) {
            is SlowMotionState.Armed -> {
                if (current.lease != lease) SlowMotionActionResult.Rejected
                else {
                    recordTelemetryEvent(
                        SlowMotionTelemetryEventKind.STOP_REQUESTED,
                        detail = reason.name,
                    )
                    val supervisorResult = releaseSupervisor(lease, reason)
                    if (supervisorResult != MotionStopResult.Stopped) {
                        mutableState.value = SlowMotionState.Faulted(
                            "slow-motion ownership release was not confirmed",
                            lease,
                        )
                        recordStopOutcome(supervisorResult)
                        mapStopResult(supervisorResult)
                    } else {
                        mutableState.value = SlowMotionState.Idle
                        recordTelemetryEvent(SlowMotionTelemetryEventKind.STOP_CONFIRMED)
                        SlowMotionActionResult.Stopped
                    }
                }
            }
            is SlowMotionState.Holding -> {
                if (current.lease != lease) SlowMotionActionResult.Rejected
                else stopLocked(lease, reason)
            }
            else -> SlowMotionActionResult.Rejected
        }
    }

    suspend fun emergencyStop(lease: SlowMotionLease): SlowMotionActionResult = lock.withLock {
        val currentLease = when (val current = mutableState.value) {
            is SlowMotionState.Armed -> current.lease
            is SlowMotionState.Holding -> current.lease
            is SlowMotionState.Faulted -> current.lease
            SlowMotionState.Idle -> null
        }
        if (currentLease != lease) return@withLock SlowMotionActionResult.Rejected
        stopLocked(lease, MotionStopReason.EMERGENCY)
    }

    suspend fun onDisconnected(lease: SlowMotionLease): SlowMotionActionResult = lock.withLock {
        val currentLease = when (val current = mutableState.value) {
            is SlowMotionState.Armed -> current.lease
            is SlowMotionState.Holding -> current.lease
            is SlowMotionState.Faulted -> current.lease
            SlowMotionState.Idle -> null
        }
        if (currentLease != lease) return@withLock SlowMotionActionResult.Rejected
        stopLocked(lease, MotionStopReason.DISCONNECT)
    }

    /**
     * Lease-scoped recovery for a host that still retains the faulted SLOW
     * lease. It cannot clear another motion owner's supervisor state.
     */
    suspend fun recoverAfterReconnect(lease: SlowMotionLease): Boolean = lock.withLock {
        val currentLease = when (val current = mutableState.value) {
            is SlowMotionState.Armed -> current.lease
            is SlowMotionState.Holding -> current.lease
            is SlowMotionState.Faulted -> current.lease
            SlowMotionState.Idle -> null
        }
        if (currentLease != lease) return@withLock false
        motionSupervisor?.recoverAfterReconnect(lease.supervisorLease)
        mutableState.value = SlowMotionState.Idle
        recordTelemetryEvent(SlowMotionTelemetryEventKind.RECOVERED)
        true
    }

    suspend fun recoverAfterReconnect() = lock.withLock {
        val sharedLease = when (val current = mutableState.value) {
            is SlowMotionState.Armed -> current.lease.supervisorLease
            is SlowMotionState.Holding -> current.lease.supervisorLease
            is SlowMotionState.Faulted -> current.lease?.supervisorLease
            SlowMotionState.Idle -> null
        }
        motionSupervisor?.recoverAfterReconnect(sharedLease)
        mutableState.value = SlowMotionState.Idle
        if (sharedLease != null) recordTelemetryEvent(SlowMotionTelemetryEventKind.RECOVERED)
    }

    private suspend fun stopLocked(
        lease: SlowMotionLease,
        reason: MotionStopReason,
    ): SlowMotionActionResult {
        recordTelemetryEvent(
            SlowMotionTelemetryEventKind.STOP_REQUESTED,
            detail = reason.name,
        )
        return try {
            when (val result = releaseSupervisor(lease, reason)) {
                MotionStopResult.Stopped -> {
                    mutableState.value = SlowMotionState.Idle
                    recordTelemetryEvent(SlowMotionTelemetryEventKind.STOP_CONFIRMED)
                    SlowMotionActionResult.Stopped
                }
                MotionStopResult.Rejected -> {
                    mutableState.value = SlowMotionState.Faulted("slow-motion stop was rejected", lease)
                    recordTelemetryEvent(SlowMotionTelemetryEventKind.STOP_REJECTED)
                    SlowMotionActionResult.Rejected
                }
                is MotionStopResult.Unknown -> {
                    mutableState.value = SlowMotionState.Faulted(result.reason, lease)
                    recordTelemetryEvent(
                        SlowMotionTelemetryEventKind.STOP_UNKNOWN,
                        detail = result.reason,
                    )
                    SlowMotionActionResult.Unknown(result.reason)
                }
            }
        } catch (error: Exception) {
            val reasonText = "slow-motion stop failed: ${error.message ?: error::class.simpleName ?: "unknown error"}"
            mutableState.value = SlowMotionState.Faulted(reasonText, lease)
            recordTelemetryEvent(
                SlowMotionTelemetryEventKind.STOP_UNKNOWN,
                detail = reasonText,
            )
            SlowMotionActionResult.Unknown(reasonText)
        }
    }

    private fun recordStopOutcome(result: MotionStopResult) {
        when (result) {
            MotionStopResult.Stopped -> recordTelemetryEvent(SlowMotionTelemetryEventKind.STOP_CONFIRMED)
            MotionStopResult.Rejected -> recordTelemetryEvent(SlowMotionTelemetryEventKind.STOP_REJECTED)
            is MotionStopResult.Unknown -> recordTelemetryEvent(
                SlowMotionTelemetryEventKind.STOP_UNKNOWN,
                detail = result.reason,
            )
        }
    }

    private fun recordTelemetryEvent(
        kind: SlowMotionTelemetryEventKind,
        atMs: Long? = null,
        command: SlowScheduleCommand? = null,
        detail: String? = null,
    ) {
        val event = SlowMotionTelemetryEvent(
            sequence = nextTelemetrySequence++,
            atMs = atMs,
            kind = kind,
            command = command,
            detail = detail,
        )
        mutableTelemetry.value = mutableTelemetry.value.copy(
            events = (mutableTelemetry.value.events + event).takeLast(MAX_SLOW_TELEMETRY_EVENTS),
        )
    }

    private suspend fun releaseSupervisor(
        lease: SlowMotionLease,
        reason: MotionStopReason,
    ): MotionStopResult = lease.supervisorLease?.let { sharedLease ->
        motionSupervisor?.stop(sharedLease, reason) ?: MotionStopResult.Unknown(
            "shared motion supervisor disappeared",
        )
    } ?: transport.stop(reason)

    private fun mapStopResult(result: MotionStopResult): SlowMotionActionResult = when (result) {
        MotionStopResult.Stopped -> SlowMotionActionResult.Stopped
        MotionStopResult.Rejected -> SlowMotionActionResult.Rejected
        is MotionStopResult.Unknown -> SlowMotionActionResult.Unknown(result.reason)
    }

    private fun requireTime(nowMs: Long) {
        require(nowMs >= 0L) { "slow-motion time must be monotonic and non-negative" }
    }
}

private const val MAX_SLOW_TELEMETRY_EVENTS = 12
