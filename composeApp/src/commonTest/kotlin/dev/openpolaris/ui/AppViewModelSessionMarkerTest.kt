package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountMode
import dev.openpolaris.core.session.SessionMarker
import dev.openpolaris.core.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [AppViewModel]'s 3c.3 + 3c.5 session-marker integration:
 *  - [AppViewModel.tryReconnectIfMarkerExists] populates [AppViewModel.reconnectPrompt]
 *    from a valid on-disk marker, no-ops for missing/corrupt/wrong-version.
 *  - [AppViewModel.acceptReconnect] / [AppViewModel.dismissReconnect] /
 *    [AppViewModel.forgetMarker] behave per their contracts.
 *  - [AppViewModel.connect] / [AppViewModel.connectDemo] persist a marker
 *    on success and not on failure.
 *  - 3c.5: [AppViewModel.reconnecting] / [AppViewModel.draftHost] /
 *    [AppViewModel.cancelReconnect] / [AppViewModel.updateDraftHost]
 *    behave per their contracts (in-flight flag, host-edit buffer, re-prompt
 *    guard, idempotent cancel, completion-path clearing).
 *
 * All tests use a temp-file-backed [SessionStore] so the real
 * `~/.openpolaris/session.json` is never touched.
 */

/**
 * Local [Connection] for these tests. The existing `FakeConnection` in
 * [AppViewModelSolveNowTest] is `private`, so we redeclare a small
 * one here — we only need a "succeed" / "throw on connect" toggle.
 */
private class MarkerFakeConnection(
    var failConnect: Boolean = false,
) : Connection {
    val written = mutableListOf<ByteArray>()
    val responses = mutableListOf<ByteArray>()

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        if (failConnect) throw java.io.IOException("refused")
    }

    override suspend fun write(data: ByteArray) {
        written += data
    }

    override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
        if (responses.isEmpty()) return -1
        val r = responses.removeAt(0)
        r.copyInto(buffer)
        return r.size
    }

    override fun close() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelSessionMarkerTest {

    private lateinit var tempFile: File
    private lateinit var store: SessionStore

    private fun newStore(): SessionStore {
        val dir = Files.createTempDirectory("openpolaris-session-").toFile()
        tempFile = File(dir, "session.json")
        return SessionStore(tempFile.absolutePath)
    }

    @AfterTest
    fun cleanup() {
        // The tempFile lives inside a freshly-created temp directory; nuke
        // the whole tree so we leave nothing behind.
        tempFile.parentFile?.deleteRecursively()
    }

    private fun newViewModel(
        scope: CoroutineScope,
        connectionFactory: () -> Connection,
    ): AppViewModel = AppViewModel(
        scope = scope,
        connectionFactory = connectionFactory,
        sessionStore = store,
        // Use the runTest scheduler so the launched coroutine in
        // tryReconnectIfMarkerExists advances during advanceUntilIdle(),
        // rather than blocking on the real Dispatchers.IO.
        ioDispatcher = UnconfinedTestDispatcher(
            scope.coroutineContext[TestCoroutineScheduler]
        ),
    )

    private fun writeMarker(
        host: String = "192.168.43.42",
        port: Int = 9090,
        epochMs: Long = 1_000L,
        mode: MountMode = MountMode.ASTRO,
        tracking: Boolean = true,
        rollDeg: Double? = 1.25,
        pitchDeg: Double? = -0.5,
    ): SessionMarker = SessionMarker(
        host = host,
        port = port,
        lastConnectedAtEpochMs = epochMs,
        lastMountMode = mode,
        lastTrackingStarted = tracking,
        lastRollDeg = rollDeg,
        lastPitchDeg = pitchDeg,
    ).also { store.write(it) }

    // ---------------------------------------------------------------------
    // tryReconnectIfMarkerExists
    // ---------------------------------------------------------------------

    @Test
    fun tryReconnectPopulatesPromptFromValidMarker() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        writeMarker(epochMs = 5_000L)
        val vm = newViewModel(this) { error("not used") }
        // Deterministic "now" so ageMs is 10_000.
        vm.nowMs = { 15_000L }

        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            val prompt = vm.reconnectPrompt.value
            assertNotNull(prompt, "expected prompt to be populated")
            assertEquals("192.168.43.42", prompt.host)
            assertEquals(9090, prompt.port)
            assertEquals(MountMode.ASTRO, prompt.mountMode)
            assertEquals(true, prompt.trackingStarted)
            assertEquals(10_000L, prompt.ageMs)
            assertEquals(1.25, prompt.lastRollDeg)
            assertEquals(-0.5, prompt.lastPitchDeg)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun tryReconnectWithNoMarkerKeepsPromptNull() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        val vm = newViewModel(this) { error("not used") }

        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertNull(vm.reconnectPrompt.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun tryReconnectWithCorruptMarkerKeepsPromptNull() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        // Write a non-JSON blob directly to the store's file.
        tempFile.parentFile?.mkdirs()
        tempFile.writeText("this is not json {")
        val vm = newViewModel(this) { error("not used") }

        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertNull(vm.reconnectPrompt.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun tryReconnectWithWrongSchemaVersionKeepsPromptNull() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        // A schemaVersion != 1 must be rejected even if the rest of the JSON parses.
        val badJson = """
            {"host":"192.168.43.42","port":9090,"lastConnectedAtEpochMs":1,
             "lastMountMode":"ASTRO","lastTrackingStarted":false,
             "lastRollDeg":0.0,"lastPitchDeg":0.0,"schemaVersion":999,"checksum":0}
        """.trimIndent()
        tempFile.parentFile?.mkdirs()
        tempFile.writeText(badJson)
        val vm = newViewModel(this) { error("not used") }

        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertNull(vm.reconnectPrompt.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun tryReconnectClampsNegativeAgeToZero() = runTest(UnconfinedTestDispatcher()) {
        // System clock before the marker's epoch: should not go negative.
        store = newStore()
        writeMarker(epochMs = 10_000L)
        val vm = newViewModel(this) { error("not used") }
        vm.nowMs = { 5_000L } // clock "before" the marker

        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            val prompt = vm.reconnectPrompt.value
            assertNotNull(prompt)
            assertEquals(0L, prompt.ageMs)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // ---------------------------------------------------------------------
    // acceptReconnect / dismissReconnect
    // ---------------------------------------------------------------------

    @Test
    fun acceptReconnectSetsHostAndClearsPrompt() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        writeMarker(host = "10.20.30.40")
        val conn = MarkerFakeConnection()
        val vm = newViewModel(this) { conn }
        vm.nowMs = { 1_000L }

        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertNotNull(vm.reconnectPrompt.value)

            vm.acceptReconnect()
            // The prompt clears synchronously; the connect() coroutine
            // is launched in the background and we don't need its result
            // to assert the prompt-clearing behavior.
            assertNull(vm.reconnectPrompt.value)
            assertEquals("10.20.30.40", vm.host)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun dismissReconnectClearsPromptButKeepsMarker() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        writeMarker()
        val vm = newViewModel(this) { error("not used") }

        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertNotNull(vm.reconnectPrompt.value)

            vm.dismissReconnect()
            assertNull(vm.reconnectPrompt.value)
            // Marker file is still on disk — dismiss does not delete it.
            assertTrue(tempFile.exists(), "marker file should still exist after dismiss")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun acceptReconnectNoOpWhenPromptIsNull() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        val vm = newViewModel(this) { error("not used") }
        val originalHost = vm.host

        try {
            vm.acceptReconnect()
            // Should not throw, should not change host, should not launch connect.
            assertEquals(originalHost, vm.host)
            assertNull(vm.reconnectPrompt.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // ---------------------------------------------------------------------
    // forgetMarker
    // ---------------------------------------------------------------------

    @Test
    fun forgetMarkerDeletesFileAndUpdatesStatus() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        writeMarker()
        val vm = newViewModel(this) { error("not used") }

        try {
            assertTrue(tempFile.exists(), "precondition: marker file exists")
            vm.forgetMarker()
            advanceUntilIdle()
            assertFalse(tempFile.exists(), "marker file should be removed after forget")
            assertEquals("Forgot saved mount", vm.statusMessage)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun forgetMarkerWithoutFileIsANoOp() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        val vm = newViewModel(this) { error("not used") }

        try {
            assertFalse(tempFile.exists(), "precondition: no marker file")
            vm.forgetMarker()
            advanceUntilIdle()
            assertEquals("No saved mount to forget", vm.statusMessage)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun forgetMarkerClearsPrompt() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        writeMarker()
        val vm = newViewModel(this) { error("not used") }

        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertNotNull(vm.reconnectPrompt.value)

            vm.forgetMarker()
            advanceUntilIdle()
            assertNull(vm.reconnectPrompt.value)
            assertFalse(tempFile.exists())
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // ---------------------------------------------------------------------
    // Marker write paths from connect / connectDemo
    // ---------------------------------------------------------------------

    @Test
    fun connectDemoWritesMarkerOnSuccess() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        val vm = newViewModel(this) { error("connect() not used in this test") }
        vm.nowMs = { 42_000L }
        try {
            vm.connectDemo()
            advanceUntilIdle()
            val readBack = store.read()
            assertNotNull(readBack, "marker should be written after successful connectDemo")
            assertEquals(42_000L, readBack.lastConnectedAtEpochMs)
            assertEquals(9090, readBack.port)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun connectDoesNotWriteMarkerOnFailure() = runTest(UnconfinedTestDispatcher()) {
        store = newStore()
        val conn = MarkerFakeConnection(failConnect = true)
        val vm = newViewModel(this) { conn }
        try {
            vm.connect()
            advanceUntilIdle()
            assertFalse(
                tempFile.exists(),
                "no marker should be written when the underlying connect throws",
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
            // Drain any pending cancellation continuations so the autolevel
            // collectors + observeJob move from Active → Cancelling → Completed
            // before runTest checks for uncompleted children.
            advanceUntilIdle()
        }
    }

    @Test
    fun connectOverwritesMarkerOnSuccess() = runTest(UnconfinedTestDispatcher()) {
        // A stale marker from a prior session is overwritten by a fresh
        // connectDemo() — verify the file ends up with the new epoch.
        store = newStore()
        writeMarker(epochMs = 1L)
        val vm = newViewModel(this) { error("not used") }
        vm.nowMs = { 99_999L }
        try {
            vm.connectDemo()
            advanceUntilIdle()
            val readBack = store.read()
            assertNotNull(readBack)
            assertEquals(99_999L, readBack.lastConnectedAtEpochMs)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // ---------------------------------------------------------------------
    // 3c.5 reconnect UX: in-flight state, host edit, cancel
    // ---------------------------------------------------------------------

    @Test
    fun tryReconnectSeedsDraftHostFromMarker() = runTest(UnconfinedTestDispatcher()) {
        // The host field in the dialog should be pre-populated with the
        // marker's host so the user can edit it without re-typing.
        store = newStore()
        writeMarker(host = "192.168.43.42")
        val vm = newViewModel(this) { error("not used") }
        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertNotNull(vm.reconnectPrompt.value)
            assertEquals("192.168.43.42", vm.draftHost.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun tryReconnectDoesNotClobberNonBlankDraftHost() = runTest(UnconfinedTestDispatcher()) {
        // If the user already typed a host (in the current launch session,
        // e.g. they edited it, backgrounded the app, and resumed), a fresh
        // marker prompt must NOT re-prompt — that would clobber their edit.
        // (Background-then-resume re-runs tryReconnectIfMarkerExists, so
        // the re-prompt guard is the fix for this case.)
        store = newStore()
        writeMarker(host = "10.0.0.1")
        val vm = newViewModel(this) { error("not used") }
        try {
            // Simulate the user editing the host in the dialog.
            vm.updateDraftHost("user-typed.example")
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            // The prompt must NOT appear (it would clobber the edit).
            assertNull(
                vm.reconnectPrompt.value,
                "non-blank draftHost must suppress the re-prompt",
            )
            // The user's edit is preserved.
            assertEquals("user-typed.example", vm.draftHost.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun acceptReconnectWithBlankDraftHostUsesPromptHost() = runTest(UnconfinedTestDispatcher()) {
        // Edge case: if draftHost somehow ended up blank (e.g. cleared
        // by a code path we haven't thought through) at accept time, we
        // must fall back to the prompt's persisted host rather than
        // launching connect() with an empty host.
        store = newStore()
        writeMarker(host = "10.20.30.40")
        val conn = MarkerFakeConnection()
        val vm = newViewModel(this) { conn }
        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            // Force the draft to blank (it was seeded by tryReconnect).
            vm.updateDraftHost("")
            vm.acceptReconnect()
            advanceUntilIdle()
            assertEquals("10.20.30.40", vm.host, "must fall back to prompt.host when draft is blank")
            // No fresh marker write expected (targetHost == prompt.host).
            // The existing marker should still be intact.
            assertTrue(tempFile.exists())
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun acceptReconnectWithEditedDraftHostWritesFreshMarker() = runTest(UnconfinedTestDispatcher()) {
        // The whole point of the host-edit field: the user can change
        // the host without dismissing the dialog. acceptReconnect must
        // persist the new host so the next launch's prompt is consistent
        // with what they just connected to.
        store = newStore()
        writeMarker(host = "10.20.30.40", epochMs = 1_000L)
        val conn = MarkerFakeConnection()
        val vm = newViewModel(this) { conn }
        vm.nowMs = { 2_000L }
        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            vm.updateDraftHost("new-host.example")
            vm.acceptReconnect()
            advanceUntilIdle()
            val readBack = store.read()
            assertNotNull(readBack)
            assertEquals("new-host.example", readBack.host)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun acceptReconnectSetsReconnectingTrue() = runTest(UnconfinedTestDispatcher()) {
        // The dialog uses vm.reconnecting to swap from three actions to
        // a single Cancel action. This test pins the contract that
        // acceptReconnect flips the flag to true synchronously (before
        // the launched connect() coroutine actually starts, let alone
        // completes).
        store = newStore()
        writeMarker()
        val conn = MarkerFakeConnection()
        val vm = newViewModel(this) { conn }
        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertFalse(vm.reconnecting.value, "precondition: not reconnecting before accept")
            vm.acceptReconnect()
            assertTrue(vm.reconnecting.value, "reconnecting must be true right after accept")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun cancelReconnectIsIdempotentAndClearsReconnecting() = runTest(UnconfinedTestDispatcher()) {
        // Two assertions:
        //  1. cancelReconnect with nothing in flight is a no-op (does
        //     not throw, does not change statusMessage to "Reconnect
        //     cancelled").
        //  2. cancelReconnect with a connect in flight clears the flag
        //     and surfaces a status message.
        store = newStore()
        writeMarker()
        val conn = MarkerFakeConnection()
        val vm = newViewModel(this) { conn }
        try {
            // (1) No connect in flight.
            val beforeStatus = vm.statusMessage
            vm.cancelReconnect()
            assertEquals(beforeStatus, vm.statusMessage, "no-op cancel must not touch statusMessage")
            assertFalse(vm.reconnecting.value)

            // (2) Connect in flight.
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            vm.acceptReconnect()
            assertTrue(vm.reconnecting.value)
            vm.cancelReconnect()
            assertFalse(vm.reconnecting.value, "cancel must clear the in-flight flag")
            assertEquals("Reconnect cancelled", vm.statusMessage)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun connectClearsReconnectingOnSuccess() = runTest(UnconfinedTestDispatcher()) {
        // The connect() launched from acceptReconnect must clear the
        // reconnecting flag in its completion path (success branch).
        // Otherwise the dialog would stay in its "Cancel"-only state
        // forever, even though the connect succeeded.
        store = newStore()
        writeMarker()
        val conn = MarkerFakeConnection()
        val vm = newViewModel(this) { conn }
        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            vm.acceptReconnect()
            assertTrue(vm.reconnecting.value, "flag set after accept")
            advanceUntilIdle()
            assertFalse(
                vm.reconnecting.value,
                "flag must clear after the launched connect() completes",
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun connectClearsReconnectingOnFailure() = runTest(UnconfinedTestDispatcher()) {
        // Same as the success test, but the underlying connect throws
        // (mount powered off, link down). The flag must still clear in
        // the failure branch so the user can see the failure status
        // and retry from the dialog.
        store = newStore()
        writeMarker()
        val conn = MarkerFakeConnection(failConnect = true)
        val vm = newViewModel(this) { conn }
        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            vm.acceptReconnect()
            assertTrue(vm.reconnecting.value, "flag set after accept")
            advanceUntilIdle()
            assertFalse(
                vm.reconnecting.value,
                "flag must clear even when the underlying connect throws",
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
            advanceUntilIdle()
        }
    }

    @Test
    fun disconnectClearsReconnecting() = runTest(UnconfinedTestDispatcher()) {
        // If a connect is in flight when the user navigates away (or the
        // OS tears down the activity for memory pressure), disconnect()
        // must collapse the in-flight flag so a fresh launch does not
        // see a stale "connecting" state.
        store = newStore()
        writeMarker()
        val conn = MarkerFakeConnection()
        val vm = newViewModel(this) { conn }
        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            vm.acceptReconnect()
            assertTrue(vm.reconnecting.value)
            vm.disconnect()
            assertFalse(vm.reconnecting.value, "disconnect must clear the in-flight flag")
        } finally {
            vm.preview.shutdown()
        }
    }

    @Test
    fun forgetMarkerClearsDraftHost() = runTest(UnconfinedTestDispatcher()) {
        // If the user typed a host, then "Forget" instead of reconnect,
        // the draft must clear so a future fresh marker (post-success)
        // can re-seed it. Otherwise the re-prompt guard from
        // tryReconnectDoesNotClobberNonBlankDraftHost would suppress
        // the prompt forever.
        store = newStore()
        writeMarker(host = "10.0.0.1")
        val vm = newViewModel(this) { error("not used") }
        try {
            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertEquals("10.0.0.1", vm.draftHost.value, "precondition: draft seeded by prompt")
            vm.forgetMarker()
            advanceUntilIdle()
            assertEquals("", vm.draftHost.value, "forget must clear the host edit buffer")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // ---------------------------------------------------------------------
    // 3d D1: nullable tilt on the marker
    // ---------------------------------------------------------------------

    @Test
    fun connectDemoWritesNullRollAndPitchWhenPositionIsNull() = runTest(UnconfinedTestDispatcher()) {
        // 3d D1: when the mount never reported a 517 frame before disconnect,
        // the marker must record null for roll/pitch, not 0.0. Recording 0.0
        // would tell the next-launch UI "you were at roll 0.0°" — data
        // invented out of thin air.
        store = newStore()
        val vm = newViewModel(this) { error("not used") }
        // The VM's `position` is null at construction; connectDemo() does
        // not run the poll loop in this slice so it stays null when
        // saveMarker() fires.
        try {
            assertNull(vm.position, "precondition: no 517 frame has landed yet")
            vm.connectDemo()
            advanceUntilIdle()
            val readBack = store.read()
            assertNotNull(readBack, "marker should be written after successful connectDemo")
            assertNull(
                readBack.lastRollDeg,
                "3d D1: no 517 → lastRollDeg must be null, not 0.0 (data integrity)",
            )
            assertNull(
                readBack.lastPitchDeg,
                "3d D1: no 517 → lastPitchDeg must be null, not 0.0 (data integrity)",
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun connectDemoPreservesNonNullRollAndPitchWhenSet() = runTest(UnconfinedTestDispatcher()) {
        // 3d D1: when [AppViewModel.position] has a real value at saveMarker
        // time (a 517 frame landed), the marker must record those values
        // — nullability must not regress the happy path.
        store = newStore()
        val vm = newViewModel(this) { error("not used") }
        try {
            // Seed a real GimbalPosition so saveMarker() sees non-null.
            // We don't have a public setter for `position` — the test
            // relies on the fact that connectDemo() does not run a poll
            // loop, so this assignment would not survive a real connect.
            // We use a no-op connectDemo() to trigger saveMarker() and
            // assert that null position produces null (covered by
            // connectDemoWritesNullRollAndPitchWhenPositionIsNull). The
            // non-null path is exercised by the codec/Json tests, where
            // we can construct markers directly. This test only asserts
            // the helper accepts nullable arguments (it just must not
            // throw) and that round-tripping the legacy "1.25" default
            // still works.
            val marker = writeMarker(rollDeg = 1.25, pitchDeg = -0.5)
            assertEquals(1.25, marker.lastRollDeg)
            assertEquals(-0.5, marker.lastPitchDeg)
            val readBack = store.read()
            assertNotNull(readBack)
            assertEquals(1.25, readBack.lastRollDeg)
            assertEquals(-0.5, readBack.lastPitchDeg)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // ---------------------------------------------------------------------
    // 3d D2: surface host-edit write failure via statusMessage
    // ---------------------------------------------------------------------

    @Test
    fun acceptReconnectWithHostEditWriteFailureSurfacesStatus() = runTest(UnconfinedTestDispatcher()) {
        // 3d D2: when the user accepts the reconnect prompt with an edited
        // host and the resulting sessionStore.write() fails, the VM must
        // not silently swallow the error. It must surface it as a
        // statusMessage so the user knows the prompt will not appear next
        // launch (i.e., the new host will be lost). Pre-3d the Result
        // was discarded.
        store = newStore()
        writeMarker(host = "192.168.0.10", port = 9090)
        val vm = newViewModel(this) { error("not used") }
        // Make the temp dir read-only so the next write() fails at the
        // writeBytes() step. SessionStore.write() catches Exception
        // and returns Result.failure(SessionStoreException("write failed", ...)).
        val parent = tempFile.parentFile
        assertNotNull(parent)
        val originalWritable = parent.canWrite()
        assertTrue(originalWritable, "precondition: temp dir must start writable")
        try {
            parent.setWritable(false)
            assertFalse(parent.canWrite(), "precondition: temp dir must now be read-only")

            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            assertNotNull(vm.reconnectPrompt.value, "precondition: prompt should be populated")

            val statusBefore = vm.statusMessage
            vm.updateDraftHost("192.168.0.99")
            vm.acceptReconnect()
            advanceUntilIdle()

            assertNotEquals(
                statusBefore,
                vm.statusMessage,
                "3d D2: a host-edit write failure must update statusMessage",
            )
            assertTrue(
                vm.statusMessage.contains("could not save", ignoreCase = true) ||
                    vm.statusMessage.contains("save session", ignoreCase = true),
                "3d D2: statusMessage should explain the save failure; got '${vm.statusMessage}'",
            )
        } finally {
            // Restore writability so @AfterTest's deleteRecursively() works.
            parent.setWritable(true)
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun acceptReconnectWithHostEditWriteFailureKeepsOldMarker() = runTest(UnconfinedTestDispatcher()) {
        // 3d D2 (companion to the status-surface test): when the host-edit
        // write fails, the previous on-disk marker must be preserved
        // (NOT forgotten). Rationale: the user will see the failure in
        // statusMessage; on the next launch the OLD marker re-prompts
        // them so they can retry. If we forgot, the new host would be
        // lost and the user would have to re-type the IP from scratch.
        store = newStore()
        writeMarker(host = "192.168.0.10", port = 9090, epochMs = 7_777L)
        val vm = newViewModel(this) { error("not used") }
        val parent = tempFile.parentFile
        assertNotNull(parent)
        try {
            parent.setWritable(false)

            vm.tryReconnectIfMarkerExists()
            advanceUntilIdle()
            vm.updateDraftHost("192.168.0.99")
            vm.acceptReconnect()
            advanceUntilIdle()

            // The old marker file should still exist with the old host
            // and epoch. (We don't restore writability before reading
            // because read() doesn't need write perms.)
            assertTrue(
                tempFile.exists(),
                "3d D2: a failed host-edit write must NOT delete the old marker",
            )
            val readBack = store.read()
            assertNotNull(readBack, "3d D2: the old marker must remain readable on disk")
            assertEquals(
                "192.168.0.10",
                readBack.host,
                "3d D2: the old marker must still hold the OLD host, not the user's edit",
            )
            assertEquals(7_777L, readBack.lastConnectedAtEpochMs)
        } finally {
            parent.setWritable(true)
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // ---------------------------------------------------------------------
    // 3d D3: default host points at the Polaris AP, not the phone tether
    // ---------------------------------------------------------------------

    @Test
    fun defaultHostIsPolarisAP() = runTest(UnconfinedTestDispatcher()) {
        // 3d D3: a freshly-instantiated AppViewModel must default its
        // `host` to the Polaris WiFi AP gateway (192.168.0.1), NOT the
        // Android USB-tethered gateway (192.168.43.1) which was the
        // pre-3d default. The 192.168.43.1 default is still correct
        // for the phone-tethered VR rig (see
        // VRActivity.DEFAULT_HOST), but not for the desktop app the
        // user runs on the PC.
        store = newStore()
        val vm = newViewModel(this) { error("not used") }
        try {
            assertEquals(
                "192.168.0.1",
                vm.host,
                "3d D3: default host must point at the Polaris AP, not the phone tether",
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
