package dev.openpolaris.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android implementation of the Benro Connect Bluetooth wake pulse.
 *
 * The source-aligned operation is deliberately small: find the Polaris BLE
 * device, open a GATT connection, observe STATE_CONNECTED (the source's
 * state=2), then close it. No characteristic write is sent. The mount's
 * firmware uses the incoming GATT connection itself to bring up the Wi-Fi AP.
 */
class MountBluetoothWake(private val context: Context) {

    data class Result(
        val success: Boolean,
        val detail: String,
        val deviceName: String? = null,
    )

    @SuppressLint("MissingPermission")
    suspend fun wake(progress: suspend (String) -> Unit = {}): Result {
        val adapter = bluetoothAdapter()
            ?: return Result(false, "Bluetooth adapter is unavailable")
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        ) {
            return Result(false, "Bluetooth Connect permission is required")
        }
        if (!adapter.isEnabled) {
            return Result(false, "Bluetooth is disabled; enable it and retry")
        }

        progress("Bluetooth wake requested")
        val device = bondedPolarisDevice(adapter)
            ?: run {
                progress("Bluetooth scanning for Polaris device…")
                discoverPolarisDevice(adapter)
            }
            ?: return Result(
                false,
                "No Polaris BLE device found; the Wi-Fi scan will still run",
            )

        val name = deviceName(device)
        progress("Connecting GATT to ${name ?: device.address}…")
        val connected = openGattPulse(device)
        return if (connected) {
            progress("Bluetooth GATT pulse opened for ${name ?: device.address}")
            Result(true, "GATT state=2 observed; wake pulse delivered", name)
        } else {
            Result(
                false,
                "GATT connection did not reach state=2 for ${name ?: device.address}",
                name,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothAdapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    private fun bondedPolarisDevice(adapter: BluetoothAdapter): BluetoothDevice? =
        runCatching {
            adapter.bondedDevices.firstOrNull { isPolarisName(deviceName(it)) }
        }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun discoverPolarisDevice(adapter: BluetoothAdapter): BluetoothDevice? {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        ) {
            return null
        }
        val scanner = runCatching { adapter.bluetoothLeScanner }.getOrNull() ?: return null
        return withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                lateinit var callback: ScanCallback

                fun finish(device: BluetoothDevice?) {
                    if (!finished.compareAndSet(false, true)) return
                    runCatching { scanner.stopScan(callback) }
                    if (continuation.isActive) continuation.resume(device)
                }

                callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val name = result.scanRecord?.deviceName ?: deviceName(result.device)
                        if (isPolarisName(name)) finish(result.device)
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>) {
                        results.firstOrNull {
                            isPolarisName(it.scanRecord?.deviceName ?: deviceName(it.device))
                        }?.let { finish(it.device) }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        finish(null)
                    }
                }

                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) {
                        runCatching { scanner.stopScan(callback) }
                    }
                }
                try {
                    @SuppressLint("MissingPermission")
                    scanner.startScan(callback)
                } catch (_: SecurityException) {
                    finish(null)
                } catch (_: RuntimeException) {
                    finish(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openGattPulse(device: BluetoothDevice): Boolean =
        withTimeoutOrNull(GATT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                var gatt: BluetoothGatt? = null

                fun finish(success: Boolean) {
                    if (!finished.compareAndSet(false, true)) return
                    val current = gatt
                    if (current != null) {
                        runCatching { current.disconnect() }
                        runCatching { current.close() }
                    }
                    if (continuation.isActive) continuation.resume(success)
                }

                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int,
                    ) {
                        if (status == BluetoothGatt.GATT_SUCCESS &&
                            newState == BluetoothProfile.STATE_CONNECTED
                        ) {
                            // STATE_CONNECTED is the source-aligned state=2
                            // wake evidence. Do not discover services or write
                            // a characteristic: the bare connection is the
                            // complete Benro wake operation.
                            finish(true)
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            finish(false)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) {
                        gatt?.let {
                            runCatching { it.disconnect() }
                            runCatching { it.close() }
                        }
                    }
                }
                gatt = try {
                    device.connectGatt(
                        context,
                        false,
                        callback,
                        BluetoothDevice.TRANSPORT_LE,
                    )
                } catch (_: SecurityException) {
                    null
                } catch (_: RuntimeException) {
                    null
                }
                if (gatt == null) finish(false)
            }
        } ?: false

    @SuppressLint("MissingPermission")
    private fun deviceName(device: BluetoothDevice): String? =
        runCatching { device.name?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

    private fun isPolarisName(name: String?): Boolean {
        val normalized = name?.trim()?.lowercase() ?: return false
        return normalized.startsWith("polaris_") || normalized.startsWith("theta_")
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val SCAN_TIMEOUT_MS: Long = 8_000
        const val GATT_TIMEOUT_MS: Long = 15_000
    }
}
