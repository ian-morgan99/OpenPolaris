package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.CameraController
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.PreviewTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
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
        val dynamicResponses = mutableMapOf<Int, String>()
        var holdInfoWrites = false
        val infoWriteGate = CompletableDeferred<Unit>()
        private val infoCodes = setOf(265, 266, 267, 268, 275)

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit

        override suspend fun write(data: ByteArray) {
            val wire = String(data, Charsets.US_ASCII)
            written += wire
            val code = wire
                .removePrefix("1&")
                .substringBefore("&")
                .toIntOrNull()
            if (holdInfoWrites && code in infoCodes) {
                infoWriteGate.await()
            }
            val response = when {
                wire.startsWith("1&284&") -> "1&284&2&mode:0;#"
                wire.startsWith("1&820&") -> "1&820&2&needed:0;#"
                wire.startsWith("1&286&") ->
                    "1&286&2&manufacturer:Pentax;model:K-3 III;state:1;storage:128;photoFormat:1;#"
                wire.startsWith("1&282&") -> "1&282&4&format:RAW;#"
                wire.startsWith("1&296&") -> "1&296&2&mode:1;#"
                wire.startsWith("1&298&") -> "1&298&2&ExTime:1250;#"
                wire.startsWith("1&306&") -> "1&306&2&type:1;#"
                code in infoCodes -> dynamicResponses[code]
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
    fun auxiliaryReadbackUsesExplicitSourceAlignedQueries() = runTest(UnconfinedTestDispatcher()) {
        FeatureFlags.reset()
        FeatureFlags.enable("experimentalCamera")
        val connection = FakeConnection()
        val vm = vm(this, connection)
        try {
            vm.connect()
            runCurrent()

            vm.refreshCameraAuxiliary()
            runCurrent()

            assertEquals("RAW", vm.cameraAuxiliaryReadback.imageFormat?.value)
            assertEquals("1", vm.cameraAuxiliaryReadback.controlMode?.value)
            assertEquals("1250", vm.cameraAuxiliaryReadback.exposureTime?.value)
            assertEquals("1&306&2&#", connection.written.last())
            assertTrue(connection.count(282) > 0)
            assertTrue(connection.count(296) > 0)
            assertTrue(connection.count(298) > 0)
            assertTrue(connection.count(306) > 0)
        } finally {
            FeatureFlags.reset()
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun emptyAndMissingFieldsPreserveLastGoodValuesAndExposeIndividualStatuses() =
        runTest(UnconfinedTestDispatcher()) {
            FeatureFlags.reset()
            FeatureFlags.enable("experimentalCamera")
            val connection = FakeConnection().apply {
                dynamicResponses.putAll(
                    mapOf(
                        265 to "1&265&2&RD:1;V:2;R:100,200,400;#",
                        266 to "1&266&2&RD:1;V:1;R:Auto,Daylight;#",
                        267 to "1&267&2&RD:1;V:3;R:-2,-1,0,1;#",
                        268 to "1&268&2&RD:1;V:4;R:1/30,1/60,1/125;#",
                        275 to "1&275&2&RD:1;V:5;R:f/1.4,f/2;#",
                    ),
                )
            }
            val vm = vm(this, connection)
            try {
                vm.connect()
                runCurrent()

                vm.refreshCamera()
                runCurrent()

                val good = vm.camera
                assertEquals(CameraController.InfoStatus.VALID, vm.cameraReadbackStatuses["WB"])
                assertEquals(CameraController.InfoStatus.VALID, vm.cameraReadbackStatuses["EV"])

                connection.dynamicResponses[266] = "1&266&2&RD:0;V:;R:;#"
                connection.dynamicResponses[267] = "1&267&2&RD:0;#"

                vm.refreshCamera()
                runCurrent()

                assertEquals(good.wbIndex, vm.camera.wbIndex)
                assertEquals(good.wbOptions, vm.camera.wbOptions)
                assertEquals(good.evIndex, vm.camera.evIndex)
                assertEquals(good.evOptions, vm.camera.evOptions)
                assertEquals(
                    CameraController.InfoStatus.EMPTY_OR_UNAVAILABLE,
                    vm.cameraReadbackStatuses["WB"],
                )
                assertEquals(
                    CameraController.InfoStatus.EMPTY_OR_UNAVAILABLE,
                    vm.cameraReadbackStatuses["EV"],
                )
                assertEquals(CameraController.InfoStatus.VALID, vm.cameraReadbackStatuses["ISO"])
                assertEquals(CameraController.InfoStatus.VALID, vm.cameraReadbackStatuses["Shutter"])
                assertEquals(CameraController.InfoStatus.VALID, vm.cameraReadbackStatuses["Aperture"])
            } finally {
                FeatureFlags.reset()
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun repeatedRefreshDoesNotOverlapAndLeavesOneReadbackSequence() =
        runTest(UnconfinedTestDispatcher()) {
            FeatureFlags.reset()
            FeatureFlags.enable("experimentalCamera")
            val connection = FakeConnection().apply {
                holdInfoWrites = true
                dynamicResponses.putAll(
                    mapOf(
                        265 to "1&265&2&RD:1;V:2;R:100,200,400;#",
                        266 to "1&266&2&RD:1;V:1;R:Auto,Daylight;#",
                        267 to "1&267&2&RD:1;V:3;R:-2,-1,0,1;#",
                        268 to "1&268&2&RD:1;V:4;R:1/30,1/60,1/125;#",
                        275 to "1&275&2&RD:1;V:5;R:f/1.4,f/2;#",
                    ),
                )
            }
            val vm = vm(this, connection)
            try {
                vm.connect()
                runCurrent()

                vm.refreshCamera()
                runCurrent()
                vm.refreshCamera()
                runCurrent()

                assertEquals("Camera parameter refresh already in progress", vm.statusMessage)
                assertEquals(1, connection.count(265))

                connection.infoWriteGate.complete(Unit)
                runCurrent()

                assertEquals(1, connection.count(265))
                assertEquals(1, connection.count(266))
                assertEquals(1, connection.count(267))
                assertEquals(1, connection.count(268))
                assertEquals(1, connection.count(275))
            } finally {
                FeatureFlags.reset()
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

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
