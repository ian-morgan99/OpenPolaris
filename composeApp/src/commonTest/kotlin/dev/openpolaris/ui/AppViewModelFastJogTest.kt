package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MotionOwner
import dev.openpolaris.core.domain.MotionState
import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelFastJogTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    @Test
    fun demoFastJogAcquiresSharedOwnerAndReleaseStopsIt() = runTest(UnconfinedTestDispatcher()) {
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
        )
        try {
            vm.connectDemo(startPolling = false)
            runCurrent()

            vm.startFastJog(Codes.GIMBAL_HADJ_SPEED)
            runCurrent()

            val owned = assertIs<MotionState.Owned>(vm.motionState.value)
            assertEquals(MotionOwner.FAST_JOG, owned.lease.owner)

            vm.stopFastJog()
            runCurrent()

            assertEquals(MotionState.Idle, vm.motionState.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
