package dev.openpolaris.ui

import dev.openpolaris.core.domain.CameraProfile
import dev.openpolaris.core.domain.CameraProfileSource
import dev.openpolaris.core.domain.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Tests [AppViewModel.cameraProfile] — issue #15's wiring seam.
 *
 * The Android-side `MainActivity.onLaunchVr` reads
 * `viewModel.cameraProfile.value` to pass FoV into VRActivity via
 * Intent extras; this is the single source of truth on the UI side.
 * The tests below pin the eyepiece default and the [setCameraProfile]
 * writer so future refactors can't silently revert to hard-coded FoV
 * (which is exactly the pre-#15 behaviour).
 *
 * No sensor stream is wired yet — the field defaults to the
 * per-mount eyepiece and is mutable via [setCameraProfile] for the
 * future mount-status collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelCameraProfileTest {

    private lateinit var tempFile: File

    private fun newViewModel(scope: CoroutineScope): AppViewModel {
        // Camera profile tests don't exercise the reconnect/save-marker
        // path, so we pass sessionStore = null to keep the VM construction
        // dependency-free. The file-backed store is exercised separately
        // in SessionStoreTest (jvmTest) and AppViewModelSessionMarkerTest.
        tempFile = File("unused")
        return AppViewModel(
            scope = scope,
            connectionFactory = { NoopConnection },
            sessionStore = null,
            ioDispatcher = UnconfinedTestDispatcher(
                scope.coroutineContext[TestCoroutineScheduler]
            ),
        )
    }

    @AfterTest
    fun cleanup() {
        tempFile.parentFile?.deleteRecursively()
    }

    @Test
    fun defaultIsPolarisEyepiece() = runTest(UnconfinedTestDispatcher()) {
        val vm = newViewModel(this)
        try {
            // Eyepiece default is the per-mount value the VRActivity used
            // to hard-code; if this changes, the production intent-extra
            // path is the thing to recheck.
            val p = vm.cameraProfile.value
            assertEquals(60f, p.fovXDeg)
            assertEquals(45f, p.fovYDeg)
            assertEquals(CameraProfileSource.PER_MOUNT_DEFAULT, p.source)
            // The companion singleton must be the same reference every
            // time; otherwise the "single source of truth" contract
            // breaks at the type level.
            assertSame(CameraProfile.PolarisEyepiece, p)
        } finally {
            // AppViewModel's ctor parents a PreviewController whose
            // internal SupervisorJob lives on the supplied scope.
            // disconnect() tears down pollJob/connectJob/autoLevel but
            // does NOT cancel that preview scope, so the test scope
            // still has an active child at runTest exit and fails with
            // UncompletedCoroutinesError. preview.shutdown() cancels
            // it. Mirrors the pattern in AppViewModelSessionMarkerTest.
            vm.disconnect()
            vm.preview.shutdown()
            advanceUntilIdle()
        }
    }

    @Test
    fun setCameraProfileUpdatesFlow() = runTest(UnconfinedTestDispatcher()) {
        val vm = newViewModel(this)
        try {
            // Sourced from a real (or future) sensor reading.
            val sensorReading = CameraProfile(90f, 67.5f, CameraProfileSource.SENSOR)
            vm.setCameraProfile(sensorReading)
            assertEquals(sensorReading, vm.cameraProfile.value)
            assertEquals(90f, vm.cameraProfile.value.fovXDeg)
            assertEquals(67.5f, vm.cameraProfile.value.fovYDeg)
            assertEquals(CameraProfileSource.SENSOR, vm.cameraProfile.value.source)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
            advanceUntilIdle()
        }
    }

    @Test
    fun setCameraProfileReplacesEyepiece() = runTest(UnconfinedTestDispatcher()) {
        val vm = newViewModel(this)
        try {
            // Pre-condition: eyepiece default is in place.
            assertSame(CameraProfile.PolarisEyepiece, vm.cameraProfile.value)
            val override = CameraProfile(40f, 30f, CameraProfileSource.OVERRIDE)
            vm.setCameraProfile(override)
            // StateFlow conflates by equals, so the new value is observable
            // and the eyepiece reference is gone.
            assertEquals(override, vm.cameraProfile.value)
            assertSame(override, vm.cameraProfile.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
            advanceUntilIdle()
        }
    }
}

/** Minimal [Connection] for these tests. The camera-profile flow has
 *  no I/O dependencies, so any connect() call would just hang; the
 *  factory is never invoked because the tests below never connect. */
private object NoopConnection : Connection {
    override suspend fun connect(host: String, port: Int, timeoutMs: Int) =
        throw UnsupportedOperationException("not used by camera-profile tests")

    override suspend fun write(data: ByteArray) =
        throw UnsupportedOperationException("not used by camera-profile tests")

    override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int =
        throw UnsupportedOperationException("not used by camera-profile tests")

    override fun close() {}
}
