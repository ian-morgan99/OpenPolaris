package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BluetoothWakeStateTest {
    @Test
    fun labelsKeepWakeSeparateFromRouteReadiness() {
        assertEquals("Not requested", BluetoothWakeState().label)
        assertEquals(
            "Pulse succeeded",
            BluetoothWakeState(
                phase = BluetoothWakePhase.SUCCEEDED,
                detail = "GATT connection opened",
            ).label,
        )
        assertEquals("Failed", BluetoothWakeState(BluetoothWakePhase.FAILED, "No GATT link").label)
    }

    @Test
    fun blankEvidenceDetailIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            BluetoothWakeState(detail = " ")
        }
    }
}
