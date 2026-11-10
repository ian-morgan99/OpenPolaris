package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.PreviewTransport
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelSessionLifecycleTest {
    private class IdleConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private fun vm(
        scope: CoroutineScope,
        connectWifi: (suspend (suspend (String) -> Unit) -> Boolean)? = null,
        wakeProbe: (suspend (suspend (String) -> Unit) -> Unit)? = null,
    ) = AppViewModel(
        scope = scope,
        connectionFactory = { IdleConnection() },
        connectWifi = connectWifi ?: { false },
        wakeProbe = wakeProbe ?: {},
        previewTransportFactory = { _, _ ->
            object : PreviewTransport {
                override fun start(host: String, port: Int, path: String) = Unit
                override fun stop() = Unit
            }
        },
    )

    @Test
    fun disconnectCancelsAnInFlightBridgeAndClearsItsState() = runTest {
        var cancelled = false
        val connectWifi: suspend (suspend (String) -> Unit) -> Boolean = {
            suspendCancellableCoroutine<Boolean> { continuation: CancellableContinuation<Boolean> ->
                continuation.invokeOnCancellation { cancelled = true }
            }
        }
        val vm = vm(this, connectWifi = connectWifi)
        try {
            vm.connectWifi()
            runCurrent()
            assertTrue(vm.reconnecting.value)

            vm.disconnect()
            runCurrent()

            assertTrue(cancelled, "disconnect must cancel the bridge operation")
            assertFalse(vm.reconnecting.value, "disconnect must clear bridge progress")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun bridgeTakesOwnershipFromAStandaloneWake() = runTest {
        var wakeCancelled = false
        var bridgeStarted = false
        val wakeProbe: suspend (suspend (String) -> Unit) -> Unit = {
            suspendCancellableCoroutine<Unit> { continuation: CancellableContinuation<Unit> ->
                continuation.invokeOnCancellation { wakeCancelled = true }
            }
        }
        val connectWifi: suspend (suspend (String) -> Unit) -> Boolean = {
            bridgeStarted = true
            suspendCancellableCoroutine<Boolean> { _: CancellableContinuation<Boolean> -> }
        }
        val vm = vm(this, connectWifi = connectWifi, wakeProbe = wakeProbe)
        try {
            vm.wake()
            runCurrent()
            assertTrue(vm.waking.value)

            vm.connectWifi()
            runCurrent()

            assertTrue(wakeCancelled, "bridge bring-up must cancel a prior standalone wake")
            assertTrue(bridgeStarted, "bridge bring-up should own the active operation")
            assertFalse(vm.waking.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectCancelsAnInFlightBluetoothWake() = runTest {
        var cancelled = false
        val wakeProbe: suspend (suspend (String) -> Unit) -> Unit = {
            suspendCancellableCoroutine<Unit> { continuation: CancellableContinuation<Unit> ->
                continuation.invokeOnCancellation { cancelled = true }
            }
        }
        val vm = vm(this, wakeProbe = wakeProbe)
        try {
            vm.wake()
            runCurrent()
            assertTrue(vm.waking.value)

            vm.disconnect()
            runCurrent()

            assertTrue(cancelled, "disconnect must cancel the Bluetooth wake")
            assertFalse(vm.waking.value, "disconnect must clear Bluetooth wake progress")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
