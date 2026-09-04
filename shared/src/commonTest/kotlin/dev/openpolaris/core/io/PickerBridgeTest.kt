package dev.openpolaris.core.io

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Issue #49: pick a file -> rotate the device -> result is lost.
 *
 * The fix is a process-scoped buffer ([PickerBridge]) the launcher
 * callback always writes to. `MainActivity.onCreate` drains it into
 * the freshly-built `AppViewModel`. These tests pin the buffer
 * state machine so a future refactor can't silently break rotation
 * again.
 *
 * The bridge is an `object` (process-wide singleton). Tests must
 * call [reset] in [AfterTest] so they don't leak state to each
 * other.
 */
class PickerBridgeTest {

    @AfterTest
    fun cleanUp() {
        PickerBridge.reset()
    }

    @Test
    fun `fresh bridge has no result and no in-flight pick`() {
        PickerBridge.reset()
        assertNull(PickerBridge.lastPickResult)
        assertFalse(PickerBridge.pickInFlight)
    }

    @Test
    fun `beginPick flips in-flight to true and does not touch the buffer`() {
        PickerBridge.beginPick()
        assertTrue(PickerBridge.pickInFlight)
        assertNull(PickerBridge.lastPickResult)
    }

    @Test
    fun `publishResult Picked stores the path and clears in-flight`() {
        PickerBridge.beginPick()
        PickerBridge.publishResult("/cache/picked.zip", PickerBridge.PickResult.Reason.Picked)
        val r = PickerBridge.lastPickResult
        assertEquals("/cache/picked.zip", r?.absolutePath)
        assertEquals(PickerBridge.PickResult.Reason.Picked, r?.reason)
        assertFalse(PickerBridge.pickInFlight)
    }

    @Test
    fun `publishResult Cancelled stores null path with Cancelled reason`() {
        PickerBridge.beginPick()
        PickerBridge.publishResult(null, PickerBridge.PickResult.Reason.Cancelled)
        val r = PickerBridge.lastPickResult
        assertNull(r?.absolutePath)
        assertEquals(PickerBridge.PickResult.Reason.Cancelled, r?.reason)
    }

    @Test
    fun `publishResult Error stores null path with Error reason`() {
        PickerBridge.beginPick()
        PickerBridge.publishResult(null, PickerBridge.PickResult.Reason.Error)
        val r = PickerBridge.lastPickResult
        assertNull(r?.absolutePath)
        assertEquals(PickerBridge.PickResult.Reason.Error, r?.reason)
    }

    @Test
    fun `publishResult overwrites a previous result`() {
        PickerBridge.beginPick()
        PickerBridge.publishResult("/old", PickerBridge.PickResult.Reason.Picked)
        PickerBridge.beginPick()
        PickerBridge.publishResult("/new", PickerBridge.PickResult.Reason.Picked)
        assertEquals("/new", PickerBridge.lastPickResult?.absolutePath)
    }

    @Test
    fun `consume returns the result and clears the buffer`() {
        PickerBridge.publishResult("/cache/picked.zip", PickerBridge.PickResult.Reason.Picked)
        val r = PickerBridge.consume()
        assertEquals("/cache/picked.zip", r?.absolutePath)
        assertEquals(PickerBridge.PickResult.Reason.Picked, r?.reason)
        assertNull(PickerBridge.lastPickResult)
    }

    @Test
    fun `consume on empty bridge returns null and does not throw`() {
        PickerBridge.reset()
        assertNull(PickerBridge.consume())
    }

    @Test
    fun `consume is single-shot - a second call returns null`() {
        PickerBridge.publishResult("/cache/picked.zip", PickerBridge.PickResult.Reason.Picked)
        val first = PickerBridge.consume()
        val second = PickerBridge.consume()
        assertEquals("/cache/picked.zip", first?.absolutePath)
        assertNull(second)
    }

    @Test
    fun `reset clears the buffer and the in-flight flag together`() {
        PickerBridge.beginPick()
        PickerBridge.publishResult("/x", PickerBridge.PickResult.Reason.Picked)
        PickerBridge.reset()
        assertNull(PickerBridge.lastPickResult)
        assertFalse(PickerBridge.pickInFlight)
    }

    /**
     * The critical rotation invariant: a result published before
     * `onDestroy` (i.e. on the old activity) must still be readable
     * by the new activity's `onCreate`. The buffer is NOT cleared
     * by `FilePickerRegistry.clear()` (which only drops the
     * activity-scoped references), so the bridge survives recreate
     * by design.
     */
    @Test
    fun `buffer survives a simulated onDestroy (no reset)`() {
        PickerBridge.beginPick()
        PickerBridge.publishResult("/cache/picked.zip", PickerBridge.PickResult.Reason.Picked)
        // Simulate MainActivity.onDestroy: registry clears, bridge does NOT.
        // (FilePickerRegistry is androidMain; we can only assert the
        // bridge side here. The `clear()` method does not call reset().)
        PickerBridge.lastPickResult // still set
        PickerBridge.pickInFlight // still false
        // The new activity's onCreate drains:
        val drained = PickerBridge.consume()
        assertEquals("/cache/picked.zip", drained?.absolutePath)
        assertEquals(PickerBridge.PickResult.Reason.Picked, drained?.reason)
    }

    @Test
    fun `full rotation flow Picked then consume`() {
        // User taps "Pick firmware". Bridge marks in-flight.
        PickerBridge.beginPick()
        assertTrue(PickerBridge.pickInFlight)
        // User picks a file. Bridge publishes.
        PickerBridge.publishResult("/cache/fw.zip", PickerBridge.PickResult.Reason.Picked)
        assertFalse(PickerBridge.pickInFlight)
        // User rotates the device. MainActivity.onDestroy runs.
        // Registry clears; bridge is preserved.
        // (Nothing in this test touches FilePickerRegistry.)
        // New MainActivity.onCreate drains.
        val result = PickerBridge.consume()!!
        assertEquals("/cache/fw.zip", result.absolutePath)
        assertEquals(PickerBridge.PickResult.Reason.Picked, result.reason)
        // Buffer is now empty - a second consume is a no-op.
        assertNull(PickerBridge.consume())
    }

    @Test
    fun `full rotation flow Cancelled then consume`() {
        PickerBridge.beginPick()
        PickerBridge.publishResult(null, PickerBridge.PickResult.Reason.Cancelled)
        val result = PickerBridge.consume()!!
        assertNull(result.absolutePath)
        assertEquals(PickerBridge.PickResult.Reason.Cancelled, result.reason)
    }
}
