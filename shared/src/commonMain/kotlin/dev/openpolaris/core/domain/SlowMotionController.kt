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

/**
 * The only wire-independent transport seam for SLOW. Implementations must
 * provide an exact, evidence-backed command mapping; this interface does not
 * serialize 532/533/534 or AC 4A and cannot make an unqualified adapter safe.
 */
interface SlowMotionTransport {
    suspend fun apply(command: SlowScheduleCommand): SlowMotionTransportResult
    suspend fun stop(reason: MotionStopReason): MotionStopResult
}

data class SlowMotionLease(val token: Long)

sealed interface SlowMotionAcquireResult {
    data class Granted(val lease: SlowMotionLease) : SlowMotionAcquireResult
    data class Busy(val lease: SlowMotionLease) : SlowMotionAcquireResult
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
) {
    init {
        require(leaseWindowMs > 0L)
    }

    private val lock = Mutex()
    private val mutableState = MutableStateFlow<SlowMotionState>(SlowMotionState.Idle)
    val state: StateFlow<SlowMotionState> = mutableState.asStateFlow()
    private var nextToken = 1L

    suspend fun acquire(): SlowMotionAcquireResult = lock.withLock {
        when (val current = mutableState.value) {
            SlowMotionState.Idle -> {
                val lease = SlowMotionLease(nextToken++)
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
            when (val result = runCatching { transport.apply(command) }.getOrElse {
                SlowMotionTransportResult.Unknown(
                    "slow-motion command failed: ${it.message ?: it::class.simpleName ?: "unknown error"}",
                )
            }) {
                SlowMotionTransportResult.Accepted -> Unit
                is SlowMotionTransportResult.Rejected -> {
                    mutableState.value = SlowMotionState.Faulted(result.reason, lease)
                    return@withLock SlowMotionActionResult.Rejected
                }
                is SlowMotionTransportResult.Unknown -> {
                    mutableState.value = SlowMotionState.Faulted(result.reason, lease)
                    return@withLock SlowMotionActionResult.Unknown(result.reason)
                }
            }
        }
        SlowMotionActionResult.Applied(commands.size)
    }

    suspend fun release(lease: SlowMotionLease): SlowMotionActionResult = lock.withLock {
        when (val current = mutableState.value) {
            is SlowMotionState.Armed -> {
                if (current.lease != lease) SlowMotionActionResult.Rejected
                else {
                    mutableState.value = SlowMotionState.Idle
                    SlowMotionActionResult.Stopped
                }
            }
            is SlowMotionState.Holding -> {
                if (current.lease != lease) SlowMotionActionResult.Rejected
                else stopLocked(lease, MotionStopReason.RELEASE)
            }
            else -> SlowMotionActionResult.Rejected
        }
    }

    suspend fun emergencyStop(lease: SlowMotionLease): SlowMotionActionResult = lock.withLock {
        val current = mutableState.value
        if (current !is SlowMotionState.Holding || current.lease != lease) {
            return@withLock SlowMotionActionResult.Rejected
        }
        stopLocked(lease, MotionStopReason.EMERGENCY)
    }

    suspend fun onDisconnected(lease: SlowMotionLease): SlowMotionActionResult = lock.withLock {
        val current = mutableState.value
        if (current !is SlowMotionState.Holding || current.lease != lease) {
            return@withLock SlowMotionActionResult.Rejected
        }
        stopLocked(lease, MotionStopReason.DISCONNECT)
    }

    suspend fun recoverAfterReconnect() = lock.withLock {
        mutableState.value = SlowMotionState.Idle
    }

    private suspend fun stopLocked(
        lease: SlowMotionLease,
        reason: MotionStopReason,
    ): SlowMotionActionResult {
        return try {
            when (val result = transport.stop(reason)) {
                MotionStopResult.Stopped -> {
                    mutableState.value = SlowMotionState.Idle
                    SlowMotionActionResult.Stopped
                }
                MotionStopResult.Rejected -> {
                    mutableState.value = SlowMotionState.Faulted("slow-motion stop was rejected", lease)
                    SlowMotionActionResult.Rejected
                }
                is MotionStopResult.Unknown -> {
                    mutableState.value = SlowMotionState.Faulted(result.reason, lease)
                    SlowMotionActionResult.Unknown(result.reason)
                }
            }
        } catch (error: Exception) {
            val reasonText = "slow-motion stop failed: ${error.message ?: error::class.simpleName ?: "unknown error"}"
            mutableState.value = SlowMotionState.Faulted(reasonText, lease)
            SlowMotionActionResult.Unknown(reasonText)
        }
    }

    private fun requireTime(nowMs: Long) {
        require(nowMs >= 0L) { "slow-motion time must be monotonic and non-negative" }
    }
}
