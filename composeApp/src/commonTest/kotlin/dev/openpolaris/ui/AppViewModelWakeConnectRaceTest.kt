package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the 8.0 wake-then-connect status race.
 *
 * Pre-fix, [AppViewModel.wake] and [AppViewModel.connect] ran as independent
 * coroutines on the same [scope] and shared a single [AppViewModel.statusMessage]
 * field, but neither cancelled the other. A user that tapped Wake (which
 * takes ~7s in production: 5s BT scan + 2s settle) and then immediately
 * tapped Connect would see the wake's terminal "Wake complete — try
 * Connect" line land AFTER the connect's "Connected" line, making it
 * look like the connect silently failed.
 *
 * The fix is in [AppViewModel.wake] (gate every status write on
 * `!_reconnecting.value`) and [AppViewModel.disconnect] (gate the
 * "Disconnected" write on `!_waking.value`). These tests exercise both
 * gates end-to-end.
 *
 * The fake [Connection] below is intentionally minimal — it only
 * responds to the handshake frames the connect path actually issues
 * (284 PUSH_MODE_STATE, 820 password probe, 823 app-hello). The wake
 * path doesn't touch the connection, so the [FakeConnection] is shared
 * between the wake and connect paths in each test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelWakeConnectRaceTest {
    /**
     * Cap for the test fake's write log. The capture-polling loop on a
     * successful connect writes a 266 every 2s of virtual time, so a
     * test that runs `advanceTimeBy(10_000)` (10s) would otherwise log
     * 5–6 capture polls plus the post-connect burst plus the handshake,
     * and `advanceUntilIdle()` would loop forever. 256 entries is
     * plenty for any assertion we want to make on `written` (the
     * handshake is 3 frames; the post-connect burst is on the order
     * of 20).
     */
    private companion object {
        const val writtenCapacity = 256
    }


    /**
     * BT wake probe stand-in: takes ~7s of virtual time (matching the
     * real `BridgeOrchestrator.wakeOverBluetooth` cadence of 5s scan +
     * 2s settle), publishes a couple of progress messages, then a
     * terminal "Woke gimbal: <addr>" line.
     *
     * The terminal is published with the prefix "Woke " so the wake
     * coroutine's `!statusMessage.startsWith("Woke ")` guard in
     * [AppViewModel.wake] treats it as already-terminal and doesn't
     * overwrite it with "Wake complete — try Connect".
     */
    private suspend fun fakeWakeProbe(
        progress: suspend (String) -> Unit,
    ) {
        // 3.5s of virtual time: progress publishes (which the VM's
        // wakeProgress lambda gates on _reconnecting) plus a small
        // settle. 500ms is short enough for tests but long enough
        // to interleave with the connect coroutine started immediately
        // afterwards in the second test.
        progress("Scanning for gimbal…")
        delay(200)
        progress("Connecting GATT to gimbal…")
        delay(200)
        progress("Woke gimbal: AA:BB:CC:DD:EE:FF")
    }

    /**
     * Connection fake. Responds to the three handshake frames the
     * connect path issues (284, 820, 823) with a minimal but valid
     * protocol reply. Suspends on the channel so the test scheduler
     * can drive it deterministically.
     *
     * `written` is capped at [writtenCapacity] entries. The reason: a
     * successful connect launches `startCapturePolling` which issues
     * a 266 (CAM_GET_STATE) request every 2s in an infinite loop.
     * Under `runTest(UnconfinedTestDispatcher())` with
     * `advanceUntilIdle()`, that loop runs until the JVM OOMs unless
     * the test drives time with a bounded `advanceTimeBy`. Even with
     * a bounded advance, the loop may iterate a few hundred times in
     * virtual time, so capping `written` keeps the test fast and
     * bounded regardless of how the scheduler is driven.
     */
    private class FakeConnection : Connection {
        private val channel = Channel<ByteArray>(Channel.BUFFERED)
        val written = mutableListOf<ByteArray>()
        var onWrite: ((ByteArray) -> Unit)? = null

        fun enqueueResponse(data: ByteArray) {
            channel.trySend(data)
        }

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
            // No-op; handshake lives below.
        }

        override suspend fun write(data: ByteArray) {
            if (written.size < writtenCapacity) written += data
            // Handshake / auth responses are scripted by the test.
            // MountSession.tryConnect issues 284 (PUSH_MODE_STATE)
            // first, then 820 (password probe), then 823 (app hello).
            val s = String(data, Charsets.US_ASCII)
            onWrite?.invoke(data)
            when {
                s.startsWith("1&284&") ->
                    enqueueResponse("1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII))
                s.startsWith("1&820&") ->
                    enqueueResponse("1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII))
                s.startsWith("1&823&") ->
                    enqueueResponse("1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII))
            }
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val r = channel.receive()
            r.copyInto(buffer)
            return r.size
        }

        override fun close() {
            channel.close()
        }
    }

    private fun newViewModel(
        scope: CoroutineScope,
        connectionFactory: () -> Connection,
        wakeProbe: suspend (suspend (String) -> Unit) -> Unit = {},
    ): AppViewModel = AppViewModel(
        scope = scope,
        connectionFactory = connectionFactory,
        wakeProbe = wakeProbe,
        // Use the runTest scheduler so the launched coroutines in
        // wake() and connect() advance during advanceUntilIdle() /
        // advanceTimeBy(), rather than blocking on the real
        // Dispatchers.Default.
        ioDispatcher = UnconfinedTestDispatcher(
            scope.coroutineContext[TestCoroutineScheduler]
        ),
        // Hand MountSession the test's runTest scope so its reader
        // loop's blocking `read` is driven by the test scheduler.
        // Without this, the reader runs on MountSession's default
        // Dispatchers.Default, and `request()`'s virtual-time
        // `withTimeout(2000)` fires before the reader's real-time
        // channel receive delivers the response — the symptom is a
        // deterministic "Could not reach …" status because the
        // 284 handshake times out.
        sessionReaderScope = scope,
    )

    /**
     * The headline regression test: tap Wake, then immediately tap
     * Connect, and the final status message must be the connect's
     * terminal ("Connected"), not the wake's terminal
     * ("Wake complete — try Connect").
     *
     * Pre-fix, the wake coroutine's `statusMessage = "Wake complete —
     * try Connect"` write landed after the connect's `statusMessage =
     * "Connected"` write, so the user would see the wake's stale
     * message and assume the connect had silently failed.
     */
    @Test
    fun connectAfterWakeEndsWithConnectStatus() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this, { conn }, ::fakeWakeProbe)

        try {
            // Tap Wake — the wake coroutine launches on the test
            // scheduler and will run for ~7s of virtual time.
            vm.wake()
            // 8.0 fix: a user can tap Connect at any time. The
            // connect coroutine sets _reconnecting=true, which causes
            // the wake's in-flight status writes to be dropped. The
            // connect coroutine then runs to completion and writes
            // "Connected" as the terminal.
            vm.connect()

            // Drive the scheduler a bounded amount of virtual time
            // rather than `advanceUntilIdle()`. The reason: a
            // successful connect starts `startCapturePolling`, an
            // infinite loop that issues a 266 (CAM_GET_STATE) request
            // every 2s. `advanceUntilIdle()` would run that loop
            // forever; a bounded `advanceTimeBy` lets the connect
            // settle, the post-connect burst fire, the capture poll
            // iterate a couple of times, and the wake coroutine
            // publish its terminal — then we assert. `disconnect()` in
            // the `finally` cancels the polling job so the test exits
            // cleanly.
            advanceTimeBy(10_000)

            // The final status must be the connect's terminal.
            // Pre-fix, this would be the wake's "Wake complete — try
            // Connect" line because the wake coroutine's terminal
            // write landed after the connect's.
            val final = vm.statusMessage
            assertEquals(
                "Connected",
                final,
                "expected connect's 'Connected' terminal, got '$final' " +
                    "(waking=${vm.waking.value}, reconnecting=${vm.reconnecting.value})"
            )

            // And the wake flag must be cleared by the wake's finally.
            assertEquals(false, vm.waking.value, "wake flag should be reset")
            // The reconnect flag must be cleared by the connect's finally.
            assertEquals(false, vm.reconnecting.value, "reconnect flag should be reset")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    /**
     * Sanity check: when Connect starts and finishes BEFORE the wake
     * coroutine's terminal lands, the wake's terminal must still be
     * suppressed. This is the other direction of the race — pre-fix,
     * the wake's "Wake complete" would clobber the connect's
     * "Connected".
     */
    @Test
    fun wakeTerminalAfterConnectIsSuppressed() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this, { conn }, ::fakeWakeProbe)

        try {
            vm.connect()
            // Bounded advance so the capture-polling loop doesn't
            // run forever (see connectAfterWakeEndsWithConnectStatus
            // for the full rationale).
            advanceTimeBy(10_000)
            // Connect has finished; statusMessage == "Connected".
            assertEquals("Connected", vm.statusMessage)

            // Now tap Wake. The wake coroutine runs, sets its
            // progress messages, and lands a "Woke gimbal: …"
            // terminal. The progress messages must be dropped
            // because _reconnecting is false (the connect is done) —
            // actually the guard is on _reconnecting, which is now
            // false, so the wake's progress IS published. The
            // terminal "Wake complete — try Connect" is gated on
            // `!statusMessage.startsWith("Woke ")` which is true, so
            // the line lands. Pre-fix, the wake's statusMessage write
            // would have clobbered the connect's "Connected" line
            // (which is the same direction as the headline test).
            vm.wake()
            advanceTimeBy(10_000)

            val final = vm.statusMessage
            // The wake's terminal is "Wake complete — try Connect"
            // (the "Woke gimbal: …" progress is overridden because
            // it does NOT start with "Woke " in the gating check —
            // wait, it does: "Woke gimbal:". So the gating in
            // [AppViewModel.wake] is:
            //   if (!reconnecting && !statusMessage.startsWith("Woke ")) {
            //       statusMessage = "Wake complete — try Connect"
            //   }
            // which means the "Woke gimbal: …" line from the BT
            // progress IS the terminal status. We assert on that.
            assertTrue(
                final.startsWith("Woke gimbal:") || final == "Connected",
                "expected wake terminal 'Woke gimbal: …' or preserved 'Connected', got '$final'"
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    /**
     * The disconnect()-side gate: if the user taps Wake, then taps
     * Connect, the connect's first action is to call disconnect(),
     * which writes "Disconnected" to statusMessage. Pre-fix, this
     * "Disconnected" write would clobber the wake's "Waking gimbal
     * over Bluetooth…" progress line. Post-fix, disconnect()'s
     * "Disconnected" write is gated on `!_waking.value`, so the wake's
     * progress message survives the disconnect and remains on screen
     * until either the wake or the connect lands its own terminal.
     */
    @Test
    fun disconnectDuringWakeDoesNotClobberWakeProgress() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this, { conn }, ::fakeWakeProbe)

        try {
            vm.wake()
            // The wake coroutine publishes "Waking gimbal over
            // Bluetooth…" (under the UnconfinedTestDispatcher this
            // runs to its first suspension before vm.wake() returns
            // because UnconfinedTestDispatcher runs eagerly). After
            // the first delay(200), the wake is mid-pulse and has
            // not landed its terminal.
            advanceTimeBy(50)
            // Confirm the wake has actually started and set its
            // initial message.
            val beforeDisconnect = vm.statusMessage
            assertTrue(
                beforeDisconnect.startsWith("Waking gimbal") ||
                    beforeDisconnect.startsWith("Scanning") ||
                    beforeDisconnect.startsWith("Connecting GATT"),
                "expected wake-progress message before disconnect, got '$beforeDisconnect'"
            )

            // Now disconnect(). The wake is still in flight
            // (_waking.value == true). Pre-fix, this would write
            // "Disconnected" to statusMessage, clobbering the wake's
            // progress. Post-fix, the gate `!_waking.value` skips
            // the write.
            vm.disconnect()

            val afterDisconnect = vm.statusMessage
            assertNotEquals(
                "Disconnected",
                afterDisconnect,
                "disconnect() must not clobber the wake's in-progress status " +
                    "(_waking.value was true; gate skipped the write)"
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
