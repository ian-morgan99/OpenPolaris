package dev.openpolaris.core.domain

import dev.openpolaris.core.solver.SolveResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM-runnable acceptance for #14 — the VR marker overlay needs to update
 * live when a re-solve completes while VRActivity is open. The contract is
 * carried by [MarkerStateBus] (a process-wide `StateFlow<SolveResult?>`):
 * AppViewModel publishes, VRActivity's collector forwards to the
 * renderer. This test asserts the bus half of that contract.
 *
 * Note: `StateFlow` always replays the current value to a new collector,
 * so a freshly-reset bus emits `null` once before any `publish` lands.
 * The assertions below account for that initial replay.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkerStateBusTest {

    @BeforeTest fun before() = MarkerStateBus.reset()
    @AfterTest fun after() = MarkerStateBus.reset()

    @Test
    fun resetYieldsNullForNewSubscribers() = runTest {
        assertNull(MarkerStateBus.solve.value)
    }

    @Test
    fun publishReachesAnActiveSubscriber() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<SolveResult?>()
        val job = launch { MarkerStateBus.solve.collect { seen += it } }

        // UnconfinedTestDispatcher runs the collector eagerly, so the
        // initial-null replay is already in `seen` by this point.
        assertEquals(1, seen.size, "expected initial null replay, got $seen")
        assertNull(seen[0])

        MarkerStateBus.publish(
            SolveResult(raDeg = 12.0, decDeg = -3.0, confidence = 0.9, matchedStars = 7, timestampMs = 1_000L)
        )
        assertEquals(2, seen.size, "expected null + one publish, got $seen")
        assertEquals(12.0, seen[1]?.raDeg)
        assertEquals(0.9, seen[1]?.confidence)
        job.cancel()
    }

    @Test
    fun lateSubscriberReceivesLatestValueOnCollect() = runTest(UnconfinedTestDispatcher()) {
        MarkerStateBus.publish(
            SolveResult(raDeg = 30.0, decDeg = 4.0, confidence = 0.8, matchedStars = 5, timestampMs = 2_000L)
        )
        MarkerStateBus.publish(
            SolveResult(raDeg = 45.0, decDeg = 5.0, confidence = 0.6, matchedStars = 4, timestampMs = 3_000L)
        )
        // StateFlow contract: a new collector immediately sees the current
        // value. That's the property the VR marker relies on — solves
        // that completed before VRActivity launched are still picked up.
        val latest = MarkerStateBus.solve.first()
        assertEquals(45.0, latest?.raDeg)
        assertEquals(0.6, latest?.confidence)
    }

    @Test
    fun secondPublishOverwritesFirst() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<SolveResult?>()
        val job = launch { MarkerStateBus.solve.collect { seen += it } }

        MarkerStateBus.publish(
            SolveResult(raDeg = 1.0, decDeg = 2.0, confidence = 0.7, matchedStars = 4, timestampMs = 1L)
        )
        MarkerStateBus.publish(
            SolveResult(raDeg = 9.0, decDeg = 8.0, confidence = 0.95, matchedStars = 10, timestampMs = 2L)
        )
        // `seen` = [null, first, second] (StateFlow replays current
        // value once, then a new emission per publish). The last value
        // is the most recent publish — a re-solve replaces, not appends.
        assertTrue(seen.size >= 3, "expected null + two publishes, got $seen")
        assertEquals(9.0, seen.last()?.raDeg)
        assertEquals(0.95, seen.last()?.confidence)
        job.cancel()
    }
}
