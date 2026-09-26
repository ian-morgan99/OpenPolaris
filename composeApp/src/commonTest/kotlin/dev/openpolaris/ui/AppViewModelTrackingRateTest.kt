package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.domain.TrackingRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for the "tracking rate is implicit" gap
 * (docs/ASTRO-WORKFLOW-HANDOVER-2026-09-17.md §5).
 *
 * The pre-fix [AppViewModel.startTracking] called `controller?.start()` with no
 * rate, producing a bare `state:1;` frame and letting the firmware pick its
 * default rate. The fix threads an explicit [TrackingRate] (default sidereal)
 * into code 531 so the wire payload always carries a `speed:` index.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTrackingRateTest {

    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        private val channel = kotlinx.coroutines.channels.Channel<ByteArray>(
            kotlinx.coroutines.channels.Channel.BUFFERED,
        )

        fun enqueueResponse(data: ByteArray) { channel.trySend(data) }
        var onWrite: ((ByteArray) -> Unit)? = null

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {}

        override suspend fun write(data: ByteArray) {
            written += data
            onWrite?.invoke(data)
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val r = channel.receive()
            r.copyInto(buffer)
            return r.size
        }

        override fun close() { channel.close() }
    }

    private fun newViewModel(scope: CoroutineScope, conn: Connection): AppViewModel =
        AppViewModel(scope = scope, connectionFactory = { conn })

    private suspend fun installSession(vm: AppViewModel, conn: Connection, scope: CoroutineScope) {
        (conn as FakeConnection).onWrite = { req ->
            val s = String(req, Charsets.US_ASCII)
            when {
                s.startsWith("1&284&") -> conn.enqueueResponse(
                    "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII),
                )
                s.startsWith("1&820&") -> conn.enqueueResponse(
                    "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII),
                )
                s.startsWith("1&823&") -> conn.enqueueResponse(
                    "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII),
                )
            }
        }

        val s = MountSession(
            connectionFactory = { conn },
            host = "127.0.0.1",
            port = 9090,
            readerScope = scope,
        )
        s.connect()
        vm.testInstallSession(s)
    }

    private fun trackingFrames(conn: FakeConnection): List<String> =
        conn.written.map { String(it, Charsets.US_ASCII) }.filter { it.contains("&531&") }

    @Test
    fun `startTracking sends an explicit sidereal speed index by default`() =
        runTest(UnconfinedTestDispatcher()) {
            val conn = FakeConnection()
            val vm = newViewModel(this, conn)
            try {
                installSession(vm, conn, this)
                advanceUntilIdle()

                // Default rate is sidereal (index 0).
                vm.startTracking()
                advanceUntilIdle()

                assertEquals(
                    listOf("1&531&2&state:1;speed:0;#"),
                    trackingFrames(conn),
                    "startTracking must send an explicit speed index, not a bare state:1;",
                )
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun `selecting lunar rate is reflected in the 531 payload`() =
        runTest(UnconfinedTestDispatcher()) {
            val conn = FakeConnection()
            val vm = newViewModel(this, conn)
            try {
                installSession(vm, conn, this)
                advanceUntilIdle()

                vm.trackingRate = TrackingRate.LUNAR
                vm.startTracking()
                advanceUntilIdle()

                assertEquals(
                    listOf("1&531&2&state:1;speed:2;#"),
                    trackingFrames(conn),
                    "lunar rate must map to speed index 2",
                )
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }
}
