package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.PreviewTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for issue #74: the MJPEG preview stream must be requested
 * from the dedicated 8080 preview endpoint, NOT from the configurable control
 * socket port (9090). Pre-fix, `MainActivity.onLaunchVr` passed
 * `viewModel.port` (the control port) into VRActivity's intent extra and the
 * transport dialed 9090 — the Polaris logged `unkown msg:GET /?action=stream`
 * on its control socket and never served frames.
 *
 * Strategy: inject a recording [PreviewTransport] factory into the VM, connect
 * with a fake connection that answers the 284/820/823 handshake, and assert
 * the transport was started on host `192.168.0.1` port **8080** even though
 * the control port is 9090.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelPreviewPortTest {

    /** Records the (host, port) each [start] call targets. */
    private class RecordingTransport : PreviewTransport {
        var lastHost: String? = null
        var lastPort: Int? = null
        var startCount: Int = 0
        override fun start(host: String, port: Int, path: String) {
            lastHost = host
            lastPort = port
            startCount += 1
        }
        override fun stop() {}
    }

    /**
     * Answers the three handshake frames the connect path issues (284, 820,
     * 823) with minimal valid protocol replies — same pattern as
     * [AppViewModelWakeConnectRaceTest].
     */
    private class FakeConnection : Connection {
        private val channel = Channel<ByteArray>(Channel.BUFFERED)

        fun enqueueResponse(data: ByteArray) {
            channel.trySend(data)
        }

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
            // No-op; the handshake happens over write/read below.
        }

        override suspend fun write(data: ByteArray) {
            val s = String(data, Charsets.US_ASCII)
            when {
                s.startsWith("1&284&") ->
                    enqueueResponse("1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII))
                s.startsWith("1&820&") ->
                    enqueueResponse("1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII))
                s.startsWith("1&823&") ->
                    enqueueResponse("1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII))
            }
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val r = channel.receive()
            r.copyInto(buffer)
            return r.size
        }

        override fun close() {
            channel.close()
        }
    }

    private fun newViewModel(
        scope: CoroutineScope,
        connectionFactory: () -> Connection,
        recording: RecordingTransport,
    ): AppViewModel = AppViewModel(
        scope = scope,
        connectionFactory = connectionFactory,
        ioDispatcher = UnconfinedTestDispatcher(scope.coroutineContext[TestCoroutineScheduler]),
        sessionReaderScope = scope,
        previewTransportFactory = { _, _ -> recording },
        previewIoDispatcher = UnconfinedTestDispatcher(scope.coroutineContext[TestCoroutineScheduler]),
    )

    @Test
    fun previewUsesDedicated8080EndpointRegardlessOfControlPort() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val recording = RecordingTransport()
        val vm = newViewModel(this, { conn }, recording)

        try {
            // Control port is the default 9090 — the pre-fix bug was that
            // this value leaked into the MJPEG stream URL.
            assertEquals(9090, vm.port)

            vm.connect()
            // Bounded advance: a successful connect starts the 284/517 poll
            // loop and the 266 capture poll; advanceTimeBy lets the connect
            // settle (including startPreview) without running those loops
            // forever.
            advanceTimeBy(10_000)

            assertEquals("Connected", vm.statusMessage)
            assertEquals(1, recording.startCount, "preview transport should have started exactly once")
            assertEquals("192.168.0.1", recording.lastHost)
            // The regression assertion: 8080, not the control port.
            assertEquals(8080, recording.lastPort, "MJPEG stream must target the dedicated 8080 endpoint, not the control port")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun previewFollowsExplicitPreviewPortOverride() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val recording = RecordingTransport()
        val vm = newViewModel(this, { conn }, recording)

        try {
            // A user-configured preview endpoint (e.g. a patched firmware
            // that moved the stream) must flow through to the transport.
            vm.updatePreviewPort(8081)
            assertEquals(8081, vm.previewPort)

            vm.connect()
            advanceTimeBy(10_000)

            assertEquals("Connected", vm.statusMessage)
            assertEquals(8081, recording.lastPort, "explicit preview port override must reach the transport")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
