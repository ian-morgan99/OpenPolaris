package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.SlowMotionWire
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MountSessionSlowMotionSenderTest {
    private class RecordingConnection : Connection {
        val written = mutableListOf<ByteArray>()
        private val responses = Channel<ByteArray>(Channel.BUFFERED)
        var onWrite: ((ByteArray) -> Unit)? = null

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit

        override suspend fun write(data: ByteArray) {
            written += data
            onWrite?.invoke(data)
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val response = responses.receive()
            response.copyInto(buffer)
            return response.size
        }

        override fun close() {
            responses.close()
        }

        fun enqueue(text: String) {
            responses.trySend(text.encodeToByteArray())
        }
    }

    private fun connectResponses(connection: RecordingConnection, result: Int = 0) {
        connection.onWrite = { request ->
            val wire = request.decodeToString()
            when {
                wire.startsWith("1&284&") -> connection.enqueue("1&284&2&mode:0;#")
                wire.startsWith("1&820&") -> connection.enqueue("820@needed:0;#")
                wire.startsWith("1&823&") -> connection.enqueue("823@app:openpolaris;ver:test;#")
                wire.startsWith("1&532&") -> connection.enqueue("532@ret:" + result + ";#")
            }
        }
    }

    private fun frame() = SlowMotionWire.encode(
        SlowScheduleCommand(
            axis = RateAxis.M1_PAN,
            kind = SlowScheduleKind.SLOW,
            point = SlowMotionPoint(rate = 0.0059, key = 0, state = 1, level = 1),
        ),
    )

    @Test
    fun sendsSourceAlignedFrameAndRequiresRetZero() = runTest(UnconfinedTestDispatcher()) {
        val connection = RecordingConnection()
        connectResponses(connection)
        val session = MountSession({ connection }, readerScope = this)
        try {
            assertTrue(session.connect())
            val result = MountSessionSlowMotionSender.send(session, frame())
            assertEquals(SlowMotionTransportResult.Accepted, result)
            assertTrue(
                connection.written.map(ByteArray::decodeToString).any {
                    it.contains("&532&3&") && it.contains("key:0;state:1;level:1;")
                },
            )
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun nonZeroRetIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val connection = RecordingConnection()
        connectResponses(connection, result = -1)
        val session = MountSession({ connection }, readerScope = this)
        try {
            assertTrue(session.connect())
            val result = MountSessionSlowMotionSender.send(session, frame())
            val rejected = assertIs<SlowMotionTransportResult.Rejected>(result)
            assertEquals("SLOW 532 returned ret:-1", rejected.reason)
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun missingAcknowledgementIsUnknownAndQuarantinesCode() = runTest(UnconfinedTestDispatcher()) {
        val connection = RecordingConnection()
        connection.onWrite = { request ->
            val wire = request.decodeToString()
            when {
                wire.startsWith("1&284&") -> connection.enqueue("1&284&2&mode:0;#")
                wire.startsWith("1&820&") -> connection.enqueue("820@needed:0;#")
                wire.startsWith("1&823&") -> connection.enqueue("823@app:openpolaris;ver:test;#")
            }
        }
        val session = MountSession({ connection }, readerScope = this)
        try {
            assertTrue(session.connect())
            val result = async {
                MountSessionSlowMotionSender.send(session, frame())
            }
            advanceTimeBy(MountSessionSlowMotionSender.ACK_TIMEOUT_MS)
            runCurrent()
            val unknown = assertIs<SlowMotionTransportResult.Unknown>(result.await())
            assertTrue(unknown.reason.contains("reconnect required"))
            val second = MountSessionSlowMotionSender.send(session, frame())
            val secondUnknown = assertIs<SlowMotionTransportResult.Unknown>(second)
            assertTrue(secondUnknown.reason.contains("quarantined"))
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun disconnectedSessionIsUnknown() = runTest {
        val session = MountSession({ error("no connection") }, readerScope = this)
        val result = MountSessionSlowMotionSender.send(session, frame())
        val unknown = assertIs<SlowMotionTransportResult.Unknown>(result)
        assertTrue(unknown.reason.contains("not connected"))
    }
}
