package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.ConnectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class AppViewModelConnectionModeTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = -1
        override fun close() = Unit
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun modeSelectionPersistsAndRestoresWithoutNetworkSideEffects() = runTest {
        val vmScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val vm = AppViewModel(
            scope = vmScope,
            connectionFactory = { NoopConnection() },
        )
        var persisted: ConnectionMode? = null
        vm.setConnectionModePersistence { persisted = it }

        vm.selectConnectionMode(ConnectionMode.HOME_LAN)

        assertEquals(ConnectionMode.HOME_LAN, vm.connectionMode)
        assertEquals(ConnectionMode.HOME_LAN, persisted)

        vm.restoreConnectionMode(ConnectionMode.DIRECT_AP)

        assertEquals(ConnectionMode.DIRECT_AP, vm.connectionMode)
        assertEquals(
            ConnectionMode.HOME_LAN,
            persisted,
            "restore must not write the preference again",
        )
        vmScope.cancel()
    }
}
