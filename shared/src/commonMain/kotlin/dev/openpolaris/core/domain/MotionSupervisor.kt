package dev.openpolaris.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Exclusive owners of physical mount motion. */
enum class MotionOwner {
    FAST_JOG,
    SLOW_MOVE,
    GOTO,
    ALIGNMENT,
    TRACKING_CORRECTION,
    PROGRAMME,
}

enum class MotionStopReason {
    RELEASE,
    EMERGENCY,
    DISCONNECT,
    MODE_CHANGE,
    LEASE_EXPIRED,
}

data class MotionLease(
    val owner: MotionOwner,
    val token: Long,
)

sealed interface MotionAcquireResult {
    data class Granted(val lease: MotionLease) : MotionAcquireResult
    data class Busy(val active: MotionLease) : MotionAcquireResult
    data class Blocked(val reason: String) : MotionAcquireResult
}

sealed interface MotionStopResult {
    data object Stopped : MotionStopResult
    data object Rejected : MotionStopResult
    data class Unknown(val reason: String) : MotionStopResult
}

sealed interface MotionState {
    data object Idle : MotionState
    data class Owned(val lease: MotionLease) : MotionState
    /** A physical stop or ownership transition is not proven complete. */
    data class Faulted(val reason: String, val lease: MotionLease?) : MotionState
}

/** Transport seam used by the arbitration tests and future motion adapters. */
interface MotionStopTransport {
    suspend fun stop(owner: MotionOwner, reason: MotionStopReason): MotionStopResult
}

/**
 * Single-owner motion arbiter.
 *
 * This class intentionally does not send a jog, SLOW, GoTo or rate frame. It
 * makes ownership and stop ordering explicit before those transports are
 * integrated. A new owner never pre-empts an active one implicitly; callers
 * must stop/release it or use the emergency-stop path.
 */
class MotionSupervisor(
    private val stopTransport: MotionStopTransport,
) {
    private val lock = Mutex()
    private val mutableState = MutableStateFlow<MotionState>(MotionState.Idle)
    val state: StateFlow<MotionState> = mutableState.asStateFlow()

    private var nextToken = 1L

    suspend fun acquire(owner: MotionOwner): MotionAcquireResult = lock.withLock {
        when (val current = mutableState.value) {
            MotionState.Idle -> {
                val lease = MotionLease(owner, nextToken++)
                mutableState.value = MotionState.Owned(lease)
                MotionAcquireResult.Granted(lease)
            }
            is MotionState.Owned -> MotionAcquireResult.Busy(current.lease)
            is MotionState.Faulted -> MotionAcquireResult.Blocked(current.reason)
        }
    }

    suspend fun stop(lease: MotionLease, reason: MotionStopReason = MotionStopReason.RELEASE): MotionStopResult =
        lock.withLock {
            val current = (mutableState.value as? MotionState.Owned)?.lease
            if (current != lease) return@withLock MotionStopResult.Rejected
            stopAndSettle(lease, reason)
        }

    suspend fun emergencyStop(reason: MotionStopReason = MotionStopReason.EMERGENCY): MotionStopResult =
        lock.withLock {
            val lease = (mutableState.value as? MotionState.Owned)?.lease
                ?: return@withLock MotionStopResult.Stopped
            stopAndSettle(lease, reason)
        }

    /**
     * Explicitly clears a fault after the caller has re-established the mount
     * session and made any required physical recovery. This is not automatic.
     */
    suspend fun recoverAfterReconnect() = lock.withLock {
        mutableState.value = MotionState.Idle
    }

    private suspend fun stopAndSettle(lease: MotionLease, reason: MotionStopReason): MotionStopResult {
        return try {
            when (val result = stopTransport.stop(lease.owner, reason)) {
                MotionStopResult.Stopped -> {
                    mutableState.value = MotionState.Idle
                    result
                }
                MotionStopResult.Rejected -> {
                    mutableState.value = MotionState.Faulted("motion stop was rejected", lease)
                    result
                }
                is MotionStopResult.Unknown -> {
                    mutableState.value = MotionState.Faulted(result.reason, lease)
                    result
                }
            }
        } catch (error: Exception) {
            val reasonText = "motion stop failed: ${error.message ?: error::class.simpleName ?: "unknown error"}"
            mutableState.value = MotionState.Faulted(reasonText, lease)
            MotionStopResult.Unknown(reasonText)
        }
    }
}
