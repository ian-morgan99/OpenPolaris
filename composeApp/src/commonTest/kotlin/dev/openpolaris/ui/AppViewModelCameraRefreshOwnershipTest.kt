package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.PreviewTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the WBS-02.2 camera readback ownership boundary: a dynamic INFO
 * refresh belongs to the session that started it and cannot publish stale
 * options after disconnect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelCameraRefreshOwnershipTest {
    private class FakeConnection : Connection {
        private val responses = Channel<ByteArray>(Channel.BUFFERED)
        val written = mutableListOf<String>()

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit

        override suspend fun write(data: ByteArray) {
            val wire = String(data, Charsets.US_ASCII)
            written += wire
            val response = when {
                wire.startsWith("1&284&") -> "1&284&2&mode:0;#"
                wire.startsWith("1&820&") -> "1&820&2&needed:0;#"
                wire.startsWith("1&286&") ->
                    "1&286&2&manufacturer:Pentax;model:K-3 III;state:1;storage:128;photoFormat:1;#"
                else -> null
            }
            response?.let { responses.trySend(it.encodeToByteArray()) }
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val response = responses.receive()
            response.copyInto(buffer)
            return response.size
        }

        override fun close() {
            responses.close()
        }

        fun count(code: Int): Int = written.count { it.startsWith("1&$code&") }
    }

    private fun vm(scope: CoroutineScope, connection: FakeConnection) = AppViewModel(
        scope = scope,
        connectionFactory = { connection },
        sessionReaderScope = scope,
        ioDispatcher = UnconfinedTestDispatcher(
            scope.coroutineContext[TestCoroutineScheduler],
        ),
        previewTransportFactory = { _, _ ->
            object : PreviewTransport {
                override fun start(host: String, port: Int, path: String) = Unit
                override fun stop() = Unit
            }
        },
        previewIoDispatcher = UnconfinedTestDispatcher(
            scope.coroutineContext[TestCoroutineScheduler],
        ),
    )

    @Test
    fun disconnectCancelsDynamicReadbackBeforeItCanPublish() = runTest(UnconfinedTestDispatcher()) {
        FeatureFlags.reset()
        FeatureFlags.enable("experimentalCamera")
        val connection = FakeConnection()
        val vm = vm(this, connection)
        try {
            vm.connect()
            runCurrent()

            vm.refreshCamera()
            runCurrent()
            assertTrue(connection.count(265) > 0, "refresh must start the evidenced ISO INFO read")

            vm.disconnect()
            runCurrent()

            assertEquals(null, vm.cameraReadbackAtMs)
            assertEquals(null, vm.camera.isoIndex)
            assertTrue(vm.camera.isoOptions.isEmpty())
        } finally {
            FeatureFlags.reset()
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
