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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun endpointDraftsRemainSeparateForDirectApAndHomeLan() = runTest {
        val vmScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val vm = AppViewModel(
            scope = vmScope,
            connectionFactory = { NoopConnection() },
        )
        val persisted = mutableListOf<Triple<ConnectionMode, String, Int>>()
        vm.setConnectionEndpointPersistence { mode, host, port ->
            persisted += Triple(mode, host, port)
        }

        vm.updateHost("192.168.0.7")
        vm.updatePort(9091)
        vm.selectConnectionMode(ConnectionMode.HOME_LAN)
        assertEquals("", vm.host)
        assertEquals(9090, vm.port)

        vm.updateHost("10.0.0.42")
        vm.updatePort(9092)
        vm.selectConnectionMode(ConnectionMode.DIRECT_AP)
        assertEquals("192.168.0.7", vm.host)
        assertEquals(9091, vm.port)
        vm.selectConnectionMode(ConnectionMode.HOME_LAN)
        assertEquals("10.0.0.42", vm.host)
        assertEquals(9092, vm.port)
        assertEquals(Triple(ConnectionMode.HOME_LAN, "10.0.0.42", 9092), persisted.last())
        vmScope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restoredEndpointDraftsFollowTheRestoredModeWithoutPersistence() = runTest {
        val vmScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val vm = AppViewModel(
            scope = vmScope,
            connectionFactory = { NoopConnection() },
        )
        var persistenceCalls = 0
        vm.setConnectionModePersistence { persistenceCalls++ }
        vm.setConnectionEndpointPersistence { _, _, _ -> persistenceCalls++ }

        vm.restoreConnectionMode(ConnectionMode.HOME_LAN)
        vm.restoreConnectionEndpoints("192.168.0.9", 9090, "10.0.0.42", 9093)

        assertEquals("10.0.0.42", vm.host)
        assertEquals(9093, vm.port)
        assertEquals(0, persistenceCalls)
        vmScope.cancel()
    }

}
