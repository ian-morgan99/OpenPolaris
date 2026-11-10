package dev.openpolaris.core.domain

/**
 * Evidence state for the Bluetooth wake pulse. Bluetooth is only the
 * wake/control-plane handoff; it is not treated as the mount TCP session.
 */
enum class BluetoothWakePhase {
    IDLE,
    REQUESTED,
    DISCOVERING,
    CONNECTING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/**
 * User-facing evidence for the most recent Bluetooth wake attempt.
 *
 * A successful wake means a GATT pulse was reported by the host seam. It does
 * not imply that Wi-Fi, the control socket, preview or camera are ready.
 */
data class BluetoothWakeState(
    val phase: BluetoothWakePhase = BluetoothWakePhase.IDLE,
    val detail: String = "Bluetooth wake not requested in this session",
) {
    init {
        require(detail.isNotBlank()) { "Bluetooth wake detail must not be blank" }
    }

    val label: String
        get() = when (phase) {
            BluetoothWakePhase.IDLE -> "Not requested"
            BluetoothWakePhase.REQUESTED -> "Requested"
            BluetoothWakePhase.DISCOVERING -> "Discovering"
            BluetoothWakePhase.CONNECTING -> "Connecting GATT"
            BluetoothWakePhase.SUCCEEDED -> "Pulse succeeded"
            BluetoothWakePhase.FAILED -> "Failed"
            BluetoothWakePhase.CANCELLED -> "Cancelled"
        }
}
