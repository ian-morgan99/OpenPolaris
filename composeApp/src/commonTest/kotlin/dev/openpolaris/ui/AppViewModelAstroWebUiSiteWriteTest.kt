package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiSite
import dev.openpolaris.core.domain.AstroWebUiSiteClient
import dev.openpolaris.core.domain.AstroWebUiSiteControlClient
import dev.openpolaris.core.domain.AstroWebUiSiteResult
import dev.openpolaris.core.domain.AstroWebUiSiteWriteRequest
import dev.openpolaris.core.domain.AstroWebUiSiteWriteResult
import dev.openpolaris.core.domain.AstroWebUiSiteWriteState
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiSiteWriteTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeSiteClient : AstroWebUiSiteClient {
        var calls = 0

        override suspend fun read(host: String, port: Int): AstroWebUiSiteResult {
            calls += 1
            return AstroWebUiSiteResult.Success(
                AstroWebUiSite(havePosition = true, latitudeDeg = 51.5, longitudeDeg = -0.12),
            )
        }
    }

    private class FakeSiteControlClient : AstroWebUiSiteControlClient {
        val requests = mutableListOf<AstroWebUiSiteWriteRequest>()
        var release: CompletableDeferred<Unit>? = null

        override suspend fun save(
            host: String,
            port: Int,
            request: AstroWebUiSiteWriteRequest,
        ): AstroWebUiSiteWriteResult {
            requests += request
            release?.let { gate ->
                withContext(NonCancellable) { gate.await() }
            }
            return AstroWebUiSiteWriteResult.Accepted(
                AstroWebUiSite(
                    havePosition = true,
                    latitudeDeg = request.latitudeDeg,
                    longitudeDeg = request.longitudeDeg,
                ),
            )
        }
    }

    @AfterTest
    fun resetFlags() {
        FeatureFlags.reset()
    }

    private fun vm(
        siteClient: FakeSiteClient = FakeSiteClient(),
        controlClient: FakeSiteControlClient = FakeSiteControlClient(),
        scope: kotlinx.coroutines.CoroutineScope,
    ): AppViewModel {
        val vm = AppViewModel(
            scope = scope,
            connectionFactory = { NoopConnection() },
            astroWebUiSiteClient = siteClient,
            astroWebUiSiteControlClient = controlClient,
        )
        vm.testInstallSession(
            MountSession(
                connectionFactory = { NoopConnection() },
                host = "192.168.0.1",
                port = 9090,
                readerScope = scope,
            ),
        )
        vm.updateLat("51.5")
        vm.updateLng("-0.12")
        return vm
    }

    @Test
    fun siteWriteRemainsBlockedByDefault() = runTest {
        val controlClient = FakeSiteControlClient()
        val vm = vm(controlClient = controlClient, scope = this)

        try {
            vm.saveAstroWebUiSite()
            advanceUntilIdle()

            assertEquals(emptyList(), controlClient.requests)
            assertIs<AstroWebUiSiteWriteState.Failed>(vm.astroWebUiSiteWrite)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun enabledSiteWriteUsesPreviewPortAndConfirmsByReadback() = runTest {
        FeatureFlags.enable("astroWebUiSiteWrite")
        val siteClient = FakeSiteClient()
        val controlClient = FakeSiteControlClient()
        val vm = vm(siteClient = siteClient, controlClient = controlClient, scope = this)

        try {
            vm.saveAstroWebUiSite()
            advanceUntilIdle()

            assertEquals(
                listOf(AstroWebUiSiteWriteRequest(51.5, -0.12)),
                controlClient.requests,
            )
            assertIs<AstroWebUiSiteWriteState.Accepted>(vm.astroWebUiSiteWrite)
            assertEquals(1, siteClient.calls)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateSiteWriteResult() = runTest {
        FeatureFlags.enable("astroWebUiSiteWrite")
        val controlClient = FakeSiteControlClient()
        val gate = CompletableDeferred<Unit>()
        controlClient.release = gate
        val siteClient = FakeSiteClient()
        val vm = vm(siteClient = siteClient, controlClient = controlClient, scope = this)

        try {
            vm.saveAstroWebUiSite()
            runCurrent()
            assertIs<AstroWebUiSiteWriteState.Working>(vm.astroWebUiSiteWrite)

            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()

            assertIs<AstroWebUiSiteWriteState.Idle>(vm.astroWebUiSiteWrite)
            assertEquals(0, siteClient.calls)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
