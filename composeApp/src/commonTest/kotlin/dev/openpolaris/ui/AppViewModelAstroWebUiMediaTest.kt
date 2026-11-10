package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroMediaFetchResult
import dev.openpolaris.core.domain.AstroMediaItem
import dev.openpolaris.core.domain.AstroMediaListResult
import dev.openpolaris.core.domain.AstroMediaTransferKind
import dev.openpolaris.core.domain.AstroWebUiMediaClient
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiMediaTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeMediaClient : AstroWebUiMediaClient {
        var listCalls = 0
        var fetchCalls = 0
        var lastHost: String? = null
        var lastPort: Int? = null
        var lastPath: String? = null
        var lastKind: AstroMediaTransferKind? = null

        override suspend fun list(host: String, port: Int): AstroMediaListResult {
            listCalls += 1
            lastHost = host
            lastPort = port
            return AstroMediaListResult.Success(
                reportedCount = 1,
                items = listOf(AstroMediaItem("normal/IMG_0001.jpg", "normal", 12, 34)),
            )
        }

        override suspend fun fetch(
            host: String,
            port: Int,
            path: String,
            kind: AstroMediaTransferKind,
        ): AstroMediaFetchResult {
            fetchCalls += 1
            lastHost = host
            lastPort = port
            lastPath = path
            lastKind = kind
            return AstroMediaFetchResult.Success(byteArrayOf(0x01, 0x02), "image/jpeg")
        }
    }

    @AfterTest
    fun resetFlags() {
        FeatureFlags.reset()
    }

    @Test
    fun galleryUsesReadOnlyWebUiClientAndDedicatedPreviewPort() = runTest {
        val client = FakeMediaClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiMediaClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.refreshAstroMedia()
            advanceUntilIdle()
            assertEquals(1, client.listCalls)
            assertEquals(8080, client.lastPort)
            assertEquals(1, vm.astroMediaItems?.size)
            vm.selectAstroMediaCategory("normal")
            assertEquals("normal", vm.astroMediaCategory)

            vm.loadAstroMedia("normal/IMG_0001.jpg", AstroMediaTransferKind.THUMBNAIL)
            advanceUntilIdle()
            assertEquals(1, client.fetchCalls)
            assertEquals("normal/IMG_0001.jpg", client.lastPath)
            assertEquals(AstroMediaTransferKind.THUMBNAIL, client.lastKind)
            assertEquals(2, vm.astroMediaPreview?.bytes?.size)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun galleryDoesNotCallClientWithoutAnInstalledSession() = runTest {
        val client = FakeMediaClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiMediaClient = client,
        )

        try {
            vm.refreshAstroMedia()
            advanceUntilIdle()
            assertEquals(0, client.listCalls)
            assertIs<dev.openpolaris.core.domain.AstroMediaState.Failed>(vm.astroMediaState)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
