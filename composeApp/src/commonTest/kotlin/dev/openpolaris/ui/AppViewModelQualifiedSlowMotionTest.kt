package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MotionOwner
import dev.openpolaris.core.domain.MotionState
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.domain.RateAxis
import dev.openpolaris.core.domain.SlowMotionCapabilityRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelQualifiedSlowMotionTest {
    private class RecordingConnection : Connection {
        val written = mutableListOf<ByteArray>()
        private val responses = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.BUFFERED)
        var onWrite: ((ByteArray) -> Unit)? = null
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) { written += data; onWrite?.invoke(data) }
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val response = responses.receive(); response.copyInto(buffer); return response.size
        }
        override fun close() { responses.close() }
        fun enqueue(text: String) { responses.trySend(text.encodeToByteArray()) }
    }

    private fun capability() = SlowMotionCapabilityRecord(
        firmwareIdentity = "fw-test", gimbalIdentity = "gimbal-test", sourceAppIdentity = "benro-test",
        routeQualified = true, axisSignsQualified = true, rateEnvelopeQualified = true,
        acknowledgementQualified = true, watchdogQualified = true, stopQualified = true,
        disconnectRecoveryQualified = true, limitsQualified = true,
    )

    @Test
    fun qualifiedRecordUsesSourceAlignedLiveFramesAndSharedStopBoundary() = runTest(UnconfinedTestDispatcher()) {
        var clock = 1_000L
        val connection = RecordingConnection()
        connection.onWrite = { request ->
            when {
                String(request, Charsets.US_ASCII).startsWith("1&284&") -> connection.enqueue("1&284&2&mode:0;#")
                String(request, Charsets.US_ASCII).startsWith("1&820&") -> connection.enqueue("820@needed:0;#")
                String(request, Charsets.US_ASCII).startsWith("1&823&") -> connection.enqueue("823@app:openpolaris;ver:test;#")
            }
        }
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { connection },
            slowMotionCapabilityRecord = capability(),
            slowMotionNow = { clock },
        )
        val session = MountSession(connectionFactory = { connection }, host = "127.0.0.1", port = 9090, readerScope = this)
        try {
            session.connect()
            vm.testInstallSession(session)
            vm.demoSlowMotionStart(RateAxis.M1_PAN, 1, 1)
            runCurrent()
            clock += 100L
            advanceTimeBy(100L)
            runCurrent()
            val owner = assertIs<MotionState.Owned>(vm.motionState.value, "status=${vm.statusMessage}")
            assertEquals(MotionOwner.SLOW_MOVE, owner.lease.owner)
            assertTrue(
                connection.written.map { String(it, Charsets.US_ASCII) }
                    .any { it.contains("&532&") && (it.contains("state:1;") || it.contains("state:2;")) && it.contains("level:1;") },
                "status=${vm.statusMessage}; snapshot=${vm.slowMotionDemoState}; frames=${connection.written.map { String(it, Charsets.US_ASCII) }}",
            )
            vm.demoSlowMotionStop()
            runCurrent()
            assertEquals(MotionState.Idle, vm.motionState.value)
            assertTrue(
                connection.written.map { String(it, Charsets.US_ASCII) }.any { it.contains("&532&") && it.contains("state:0;") },
                "status=${vm.statusMessage}; snapshot=${vm.slowMotionDemoState}; frames=${connection.written.map { String(it, Charsets.US_ASCII) }}",
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
