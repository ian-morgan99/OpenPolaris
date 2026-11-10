package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import dev.openpolaris.core.protocol.SlowMotionWire
import dev.openpolaris.core.protocol.SlowMotionWireFrame

/**
 * Qualification-gated runtime adapter for the source-aligned 532/533/534
 * SLOW family.
 *
 * The sender must provide a positive, evidence-backed result for each frame.
 * The default OpenPolaris capability record is unqualified, so this class
 * refuses every live operation until an exact firmware/gimbal/source-app
 * record has passed the physical gates. It never falls back to AC 4A.
 */
class QualifiedSlowMotionTransport(
    private val capability: SlowMotionCapabilityRecord,
    private val send: suspend (SlowMotionWireFrame) -> SlowMotionTransportResult,
) : SlowMotionTransport {
    private val activePoints = mutableMapOf<RateAxis, SlowMotionPoint>()

    override suspend fun apply(command: SlowScheduleCommand): SlowMotionTransportResult {
        gateFailure()?.let { return SlowMotionTransportResult.Rejected(it) }

        // The scheduler can emit an idle-axis STOP without a retained point on
        // its first tick. That is a logical no-op, not a wire frame.
        if (command.kind == SlowScheduleKind.STOP && command.point == null) {
            return SlowMotionTransportResult.Accepted
        }

        val frame = try {
            SlowMotionWire.encode(command)
        } catch (error: IllegalArgumentException) {
            return SlowMotionTransportResult.Rejected(
                error.message ?: "invalid SLOW command",
            )
        }
        val result = dispatch(frame)
        if (result == SlowMotionTransportResult.Accepted) {
            when (command.kind) {
                SlowScheduleKind.SLOW -> activePoints[command.axis] = requireNotNull(command.point)
                SlowScheduleKind.STOP -> activePoints.remove(command.axis)
            }
        }
        return result
    }

    override suspend fun stop(reason: MotionStopReason): MotionStopResult {
        gateFailure()?.let { return MotionStopResult.Unknown(it) }

        val points = activePoints.toMap()
        for ((axis, point) in points) {
            val command = SlowScheduleCommand(axis, SlowScheduleKind.STOP, point)
            val frame = try {
                SlowMotionWire.encode(command)
            } catch (error: IllegalArgumentException) {
                return MotionStopResult.Unknown(
                    error.message ?: "invalid SLOW stop command",
                )
            }
            when (val result = dispatch(frame)) {
                SlowMotionTransportResult.Accepted -> Unit
                is SlowMotionTransportResult.Rejected -> return MotionStopResult.Rejected
                is SlowMotionTransportResult.Unknown -> return MotionStopResult.Unknown(result.reason)
            }
        }
        activePoints.clear()
        return MotionStopResult.Stopped
    }

    private suspend fun dispatch(frame: SlowMotionWireFrame): SlowMotionTransportResult =
        try {
            send(frame)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SlowMotionTransportResult.Unknown(
                "SLOW transport dispatch failed: " +
                    (error.message ?: error::class.simpleName ?: "unknown error"),
            )
        }

    private fun gateFailure(): String? =
        if (capability.liveReady) {
            null
        } else {
            "SLOW live transport is not qualified: " + capability.blockers.joinToString(", ")
        }
}
