package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface FastJogSendResult {
    data object Sent : FastJogSendResult
    data object Rejected : FastJogSendResult
    data class Unknown(val reason: String) : FastJogSendResult
}

interface FastJogTransport {
    suspend fun send(code: Int, durationMs: Int): FastJogSendResult
    suspend fun stop(code: Int, reason: MotionStopReason): MotionStopResult
}

data class FastJogLease(
    val code: Int,
    val token: Long,
    /** Shared physical-motion lease, when this controller participates in arbitration. */
    val supervisorLease: MotionLease? = null,
)

sealed interface FastJogAcquireResult {
    data class Granted(val lease: FastJogLease) : FastJogAcquireResult
    /** [lease] is present for a local competing hold and null for another owner. */
    data class Busy(val lease: FastJogLease? = null, val owner: MotionOwner? = null) : FastJogAcquireResult
    data class Blocked(val reason: String) : FastJogAcquireResult
}

sealed interface FastJogActionResult {
    data object Accepted : FastJogActionResult
    data object NotDue : FastJogActionResult
    data object Stopped : FastJogActionResult
    data object Rejected : FastJogActionResult
    data class Unknown(val reason: String) : FastJogActionResult
}

sealed interface FastJogState {
    data object Idle : FastJogState
    data class Armed(val lease: FastJogLease) : FastJogState
    data class Holding(val lease: FastJogLease, val lastSentMs: Long) : FastJogState
    data class Faulted(val reason: String, val lease: FastJogLease?) : FastJogState
}

/**
 * Lease/dead-man controller for the verified duration-style 513..516 jog
 * path. This is a transport seam: it does not choose a stop payload or open
 * a socket. A platform adapter must provide both the repeated duration command
 * and an evidenced stop operation before the UI can use it.
 */
class FastJogController(
    private val transport: FastJogTransport,
    private val leaseWindowMs: Long = 400L,
    private val repeatIntervalMs: Long = 50L,
    private val commandDurationMs: Int = 500,
    private val motionSupervisor: MotionSupervisor? = null,
) {
    init {
        require(leaseWindowMs > 0L)
        require(repeatIntervalMs > 0L && repeatIntervalMs <= leaseWindowMs)
        require(commandDurationMs > 0)
    }

    private val lock = Mutex()
    private val mutableState = MutableStateFlow<FastJogState>(FastJogState.Idle)
    val state: StateFlow<FastJogState> = mutableState.asStateFlow()
    private var nextToken = 1L

    suspend fun acquire(code: Int): FastJogAcquireResult = lock.withLock {
        requireValidCode(code)
        when (val current = mutableState.value) {
            FastJogState.Idle -> {
                val supervisorLease = when (val supervisor = motionSupervisor) {
                    null -> null
                    else -> when (val acquired = supervisor.acquire(MotionOwner.FAST_JOG)) {
                        is MotionAcquireResult.Granted -> acquired.lease
                        is MotionAcquireResult.Busy -> {
                            return@withLock FastJogAcquireResult.Busy(owner = acquired.active.owner)
                        }
                        is MotionAcquireResult.Blocked -> {
                            return@withLock FastJogAcquireResult.Blocked(acquired.reason)
                        }
                    }
                }
                val lease = FastJogLease(code, nextToken++, supervisorLease)
                mutableState.value = FastJogState.Armed(lease)
                FastJogAcquireResult.Granted(lease)
            }
            is FastJogState.Armed -> FastJogAcquireResult.Busy(current.lease)
            is FastJogState.Holding -> FastJogAcquireResult.Busy(current.lease)
            is FastJogState.Faulted -> FastJogAcquireResult.Blocked(current.reason)
        }
    }

    suspend fun start(lease: FastJogLease, nowMs: Long): FastJogActionResult = lock.withLock {
        requireTime(nowMs)
        val armed = (mutableState.value as? FastJogState.Armed)?.lease
        if (armed != lease) return@withLock FastJogActionResult.Rejected
        when (val result = transport.send(lease.code, commandDurationMs)) {
            FastJogSendResult.Sent -> {
                mutableState.value = FastJogState.Holding(lease, nowMs)
                FastJogActionResult.Accepted
            }
            FastJogSendResult.Rejected -> {
                mutableState.value = FastJogState.Idle
                FastJogActionResult.Rejected
            }
            is FastJogSendResult.Unknown -> {
                mutableState.value = FastJogState.Faulted(result.reason, lease)
                FastJogActionResult.Unknown(result.reason)
            }
        }
    }

    /** Send the next duration beat, or stop a lease that has expired. */
    suspend fun tick(lease: FastJogLease, nowMs: Long): FastJogActionResult = lock.withLock {
        requireTime(nowMs)
        val holding = (mutableState.value as? FastJogState.Holding)
        if (holding?.lease != lease) return@withLock FastJogActionResult.Rejected
        val elapsed = nowMs - holding.lastSentMs
        if (elapsed >= leaseWindowMs) {
            return@withLock stopLocked(lease, MotionStopReason.LEASE_EXPIRED)
        }
        if (elapsed < repeatIntervalMs) return@withLock FastJogActionResult.NotDue
        when (val result = transport.send(lease.code, commandDurationMs)) {
            FastJogSendResult.Sent -> {
                mutableState.value = FastJogState.Holding(lease, nowMs)
                FastJogActionResult.Accepted
            }
            FastJogSendResult.Rejected -> {
                mutableState.value = FastJogState.Faulted("jog beat was rejected", lease)
                FastJogActionResult.Rejected
            }
            is FastJogSendResult.Unknown -> {
                mutableState.value = FastJogState.Faulted(result.reason, lease)
                FastJogActionResult.Unknown(result.reason)
            }
        }
    }

    suspend fun release(lease: FastJogLease): FastJogActionResult = lock.withLock {
        when (val current = mutableState.value) {
            is FastJogState.Armed -> {
                if (current.lease != lease) FastJogActionResult.Rejected
                else {
                    val supervisorResult = releaseSupervisor(lease, MotionStopReason.RELEASE)
                    if (supervisorResult != MotionStopResult.Stopped) {
                        mutableState.value = FastJogState.Faulted(
                            "jog ownership release was not confirmed",
                            lease,
                        )
                        mapStopResult(supervisorResult)
                    } else {
                        mutableState.value = FastJogState.Idle
                        FastJogActionResult.Stopped
                    }
                }
            }
            is FastJogState.Holding -> {
                if (current.lease != lease) FastJogActionResult.Rejected
                else stopLocked(lease, MotionStopReason.RELEASE)
            }
            else -> FastJogActionResult.Rejected
        }
    }

    suspend fun disconnect(lease: FastJogLease): FastJogActionResult = lock.withLock {
        val current = (mutableState.value as? FastJogState.Holding)?.lease
        if (current != lease) return@withLock FastJogActionResult.Rejected
        stopLocked(lease, MotionStopReason.DISCONNECT)
    }

    suspend fun recoverAfterReconnect() = lock.withLock {
        val sharedLease = when (val current = mutableState.value) {
            is FastJogState.Armed -> current.lease.supervisorLease
            is FastJogState.Holding -> current.lease.supervisorLease
            is FastJogState.Faulted -> current.lease?.supervisorLease
            FastJogState.Idle -> null
        }
        motionSupervisor?.recoverAfterReconnect(sharedLease)
        mutableState.value = FastJogState.Idle
    }

    private suspend fun stopLocked(lease: FastJogLease, reason: MotionStopReason): FastJogActionResult {
        return try {
            when (val result = releaseSupervisor(lease, reason)) {
                MotionStopResult.Stopped -> {
                    mutableState.value = FastJogState.Idle
                    FastJogActionResult.Stopped
                }
                MotionStopResult.Rejected -> {
                    mutableState.value = FastJogState.Faulted("jog stop was rejected", lease)
                    FastJogActionResult.Rejected
                }
                is MotionStopResult.Unknown -> {
                    mutableState.value = FastJogState.Faulted(result.reason, lease)
                    FastJogActionResult.Unknown(result.reason)
                }
            }
        } catch (error: Exception) {
            val reasonText = "jog stop failed: ${error.message ?: error::class.simpleName ?: "unknown error"}"
            mutableState.value = FastJogState.Faulted(reasonText, lease)
            FastJogActionResult.Unknown(reasonText)
        }
    }

    private suspend fun releaseSupervisor(
        lease: FastJogLease,
        reason: MotionStopReason,
    ): MotionStopResult = lease.supervisorLease?.let { sharedLease ->
        motionSupervisor?.stop(sharedLease, reason) ?: MotionStopResult.Unknown(
            "shared motion supervisor disappeared",
        )
    } ?: transport.stop(lease.code, reason)

    private fun mapStopResult(result: MotionStopResult): FastJogActionResult = when (result) {
        MotionStopResult.Stopped -> FastJogActionResult.Stopped
        MotionStopResult.Rejected -> FastJogActionResult.Rejected
        is MotionStopResult.Unknown -> FastJogActionResult.Unknown(result.reason)
    }

    private fun requireValidCode(code: Int) {
        require(code in Codes.GIMBAL_HADJ_SPEED..Codes.GIMBAL_VADJ_ANGLE) {
            "not a duration-style fast-jog code: $code"
        }
    }

    private fun requireTime(nowMs: Long) {
        require(nowMs >= 0L) { "jog time must be monotonic and non-negative" }
    }
}
