package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dev.openpolaris.core.domain.PreviewTransport
import dev.openpolaris.core.domain.SupportBundleExportResult
import dev.openpolaris.core.domain.SupportBundleExportSink
import dev.openpolaris.core.domain.SupportBundleExportState
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelSupportBundleTest {
    private class IdleConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private fun vm(
        scope: CoroutineScope,
        sink: SupportBundleExportSink,
    ) = AppViewModel(
        scope = scope,
        connectionFactory = { IdleConnection() },
        supportBundleExportSink = sink,
        previewTransportFactory = { _, _ ->
            object : PreviewTransport {
                override fun start(host: String, port: Int, path: String) = Unit
                override fun stop() = Unit
            }
        },
    )

    @Test
    fun disconnectCancelsAnInFlightSupportSaveAndClearsItsState() = runTest() {
        var cancelled = false
        val sink = SupportBundleExportSink {
            suspendCancellableCoroutine<SupportBundleExportResult> { continuation: CancellableContinuation<SupportBundleExportResult> ->
                continuation.invokeOnCancellation { cancelled = true }
            }
        }
        val vm = vm(this, sink)
        try {
            vm.exportSupportBundle("test-build")
            runCurrent()
            assertIs<SupportBundleExportState.Exporting>(vm.supportBundleExportState)

            vm.disconnect()
            runCurrent()

            assertTrue(cancelled, "disconnect must cancel the platform save")
            assertIs<SupportBundleExportState.Idle>(vm.supportBundleExportState)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
