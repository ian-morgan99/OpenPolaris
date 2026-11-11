package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.SlowMotionWireFrame

/**
 * Source-aligned SLOW sender for an already connected [MountSession].
 *
 * A SLOW frame is only successful when the matching response carries ret:0.
 * The protocol has no request id, so a timeout quarantines the numeric code
 * for this session; a late response must never be applied to a later frame.
 */
object MountSessionSlowMotionSender {
    const val ACK_TIMEOUT_MS = 500L

    suspend fun send(
        session: MountSession,
        frame: SlowMotionWireFrame,
    ): SlowMotionTransportResult {
        return when (
            val acknowledgement = session.request<Int>(
                code = frame.code,
                payload = frame.payload,
                timeoutMs = ACK_TIMEOUT_MS,
                subtype = frame.subtype,
                quarantineOnTimeout = true,
            ) { response ->
                response.int("ret")
            }
        ) {
            is MountSession.CmdResult.Ok ->
                if (acknowledgement.value == 0) {
                    SlowMotionTransportResult.Accepted
                } else {
                    SlowMotionTransportResult.Rejected(
                        "SLOW " + frame.code + " returned ret:" + acknowledgement.value,
                    )
                }
            MountSession.CmdResult.Timeout ->
                SlowMotionTransportResult.Unknown(
                    "SLOW " + frame.code + " acknowledgement timed out; reconnect required",
                )
            is MountSession.CmdResult.ProtocolError ->
                SlowMotionTransportResult.Unknown(
                    "SLOW " + frame.code + " acknowledgement failed: " + acknowledgement.message,
                )
        }
    }
}
