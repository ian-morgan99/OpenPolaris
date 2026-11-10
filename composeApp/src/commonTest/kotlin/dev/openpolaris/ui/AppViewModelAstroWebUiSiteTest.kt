package dev.openpolaris.ui

import dev.openpolaris.core.domain.AstroWebUiSite
import dev.openpolaris.core.domain.AstroWebUiSiteClient
import dev.openpolaris.core.domain.AstroWebUiSiteResult
import dev.openpolaris.core.domain.AstroWebUiSiteState
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiSiteTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeSiteClient : AstroWebUiSiteClient {
        var calls = 0
        var port: Int? = null

        override suspend fun read(host: String, port: Int): AstroWebUiSiteResult {
            calls += 1
            this.port = port
            return AstroWebUiSiteResult.Success(
                AstroWebUiSite(havePosition = true, latitudeDeg = 51.5, longitudeDeg = -0.1),
            )
        }
    }

    @AfterTest
    fun resetFlags() {
        dev.openpolaris.core.config.FeatureFlags.reset()
    }

    @Test
    fun siteUsesDedicatedPreviewPortAndReportsCoordinates() = runTest {
        val client = FakeSiteClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSiteClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.refreshAstroWebUiSite()
            advanceUntilIdle()

            assertEquals(1, client.calls)
            assertEquals(8080, client.port)
            val state = assertIs<AstroWebUiSiteState.Ready>(vm.astroWebUiSite)
            assertEquals(51.5, state.site.latitudeDeg)
            assertEquals(-0.1, state.site.longitudeDeg)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun siteDoesNotCallClientWithoutAnInstalledSession() = runTest {
        val client = FakeSiteClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSiteClient = client,
        )

        try {
            vm.refreshAstroWebUiSite()
            advanceUntilIdle()

            assertEquals(0, client.calls)
            assertIs<AstroWebUiSiteState.Failed>(vm.astroWebUiSite)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
