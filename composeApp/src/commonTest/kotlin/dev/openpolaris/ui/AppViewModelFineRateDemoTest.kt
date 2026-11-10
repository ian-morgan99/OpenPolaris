package dev.openpolaris.ui

import dev.openpolaris.core.domain.MotionOwner
import dev.openpolaris.core.domain.MotionState
import dev.openpolaris.core.domain.RateAdjustState
import dev.openpolaris.core.domain.RateAxis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelFineRateDemoTest {
    @Test
    fun demoFineRateRequiresSyntheticEvidenceThenOwnsAndReleasesMotion() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = AppViewModel(
                scope = this,
                connectionFactory = { error("demo does not use the hardware connection") },
            )
            try {
                vm.connectDemo(startPolling = false)
                runCurrent()
                vm.startTracking()
                runCurrent()

                assertIs<RateAdjustState.Unavailable>(vm.fineRateDemoState.value)
                vm.prepareDemoFineRate()
                runCurrent()
                assertIs<RateAdjustState.Ready>(vm.fineRateDemoState.value)

                vm.demoFineRateApply(RateAxis.M1_PAN, 1)
                runCurrent()

                val active = assertIs<RateAdjustState.Active>(vm.fineRateDemoState.value)
                assertEquals(0.1, active.request.m1Pan)
                assertEquals(0.0, active.request.m2Tilt)
                val owner = assertIs<MotionState.Owned>(vm.motionState.value)
                assertEquals(MotionOwner.TRACKING_CORRECTION, owner.lease.owner)
                assertTrue(vm.statusMessage.contains("no hardware command"))

                vm.demoFineRateStop()
                runCurrent()

                assertIs<RateAdjustState.Ready>(vm.fineRateDemoState.value)
                assertEquals(MotionState.Idle, vm.motionState.value)
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun fineRateRehearsalBlocksConcurrentSlowMotionOwner() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = AppViewModel(
                scope = this,
                connectionFactory = { error("demo does not use the hardware connection") },
            )
            try {
                vm.connectDemo(startPolling = false)
                runCurrent()
                vm.startTracking()
                runCurrent()
                vm.prepareDemoFineRate()
                runCurrent()
                vm.demoFineRateApply(RateAxis.M2_TILT, -1)
                runCurrent()

                vm.demoSlowMotionStart(RateAxis.M1_PAN, 1, 1)
                runCurrent()

                assertIs<RateAdjustState.Active>(vm.fineRateDemoState.value)
                assertEquals(MotionOwner.TRACKING_CORRECTION, assertIs<MotionState.Owned>(vm.motionState.value).lease.owner)
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun slowMotionAdmissionBlocksCaptureOverlapInDemo() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = AppViewModel(
                scope = this,
                connectionFactory = { error("demo does not use the hardware connection") },
            )
            try {
                vm.connectDemo(startPolling = false)
                runCurrent()
                vm.testSetCapturePhaseForSafety(AppViewModel.CapturePhase.Busy)

                vm.demoSlowMotionStart(RateAxis.M1_PAN, 1, 1)
                runCurrent()

                assertEquals(MotionState.Idle, vm.motionState.value)
                assertTrue(vm.statusMessage.contains("Capture"))
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun captureStartIsBlockedWhileSlowMotionOwnsTheMount() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = AppViewModel(
                scope = this,
                connectionFactory = { error("demo does not use the hardware connection") },
            )
            try {
                vm.connectDemo(startPolling = false)
                runCurrent()
                vm.demoSlowMotionStart(RateAxis.M1_PAN, -1, 1)
                runCurrent()
                assertEquals(MotionOwner.SLOW_MOVE, assertIs<MotionState.Owned>(vm.motionState.value).lease.owner)

                vm.capture()

                assertTrue(vm.statusMessage.contains("Stop slow motion"))
                assertEquals(MotionOwner.SLOW_MOVE, assertIs<MotionState.Owned>(vm.motionState.value).lease.owner)
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun startingTrackingStopsAnActiveSlowMotionHoldBeforeModeChange() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = AppViewModel(
                scope = this,
                connectionFactory = { error("demo does not use the hardware connection") },
            )
            try {
                vm.connectDemo(startPolling = false)
                runCurrent()

                vm.demoSlowMotionStart(RateAxis.M1_PAN, 1, 1)
                runCurrent()
                assertEquals(MotionOwner.SLOW_MOVE, assertIs<MotionState.Owned>(vm.motionState.value).lease.owner)

                vm.startTracking()
                runCurrent()

                assertEquals(MotionState.Idle, vm.motionState.value)
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun enablingHalfSpeedStopsAnActiveSlowMotionHoldBeforeModeChange() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = AppViewModel(
                scope = this,
                connectionFactory = { error("demo does not use the hardware connection") },
            )
            try {
                vm.connectDemo(startPolling = false)
                runCurrent()

                vm.demoSlowMotionStart(RateAxis.M2_TILT, -1, 2)
                runCurrent()
                assertEquals(MotionOwner.SLOW_MOVE, assertIs<MotionState.Owned>(vm.motionState.value).lease.owner)

                vm.toggleHalfSpeed(true)
                runCurrent()

                assertEquals(MotionState.Idle, vm.motionState.value)
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }
}
