package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppViewModelObserverLocationTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    @Test
    fun onlyCompleteRangeValidatedLocationsArePersisted() = runTest {
        val saved = mutableListOf<Pair<Double, Double>>()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
        )
        vm.setObserverLocationPersistence { latitude, longitude ->
            saved += latitude to longitude
        }

        try {
            vm.updateLat("91")
            vm.updateLng("181")
            vm.updateLng("-0.12")
            assertTrue(saved.isEmpty())

            vm.updateLat("51.5074")
            assertEquals(listOf(51.5074 to -0.12), saved)
            assertEquals(51.5074 to -0.12, vm.observerLocationForTest())

            vm.updateLng("NaN")
            assertEquals(listOf(51.5074 to -0.12), saved)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun restoreRejectsPartialOrOutOfRangeValuesWithoutWriting() = runTest {
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
        )
        try {
            vm.restoreObserverLocation(51.5074, -0.1278)
            assertEquals(51.5074 to -0.1278, vm.observerLocationForTest())

            vm.restoreObserverLocation(91.0, 0.0)
            assertEquals(51.5074 to -0.1278, vm.observerLocationForTest())
            vm.restoreObserverLocation(null, -0.1278)
            assertEquals(51.5074 to -0.1278, vm.observerLocationForTest())
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}

private fun AppViewModel.observerLocationForTest(): Pair<Double, Double>? =
    latDeg.toDoubleOrNull()?.let { latitude ->
        lngEastDeg.toDoubleOrNull()?.let { longitude -> latitude to longitude }
    }
