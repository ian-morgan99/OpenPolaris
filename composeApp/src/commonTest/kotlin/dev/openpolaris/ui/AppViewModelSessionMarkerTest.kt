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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [AppViewModel]'s 3c.3 session-marker integration:
 *  - [AppViewModel.tryReconnectIfMarkerExists] populates [AppViewModel.reconnectPrompt]
 *    from a valid on-disk marker, no-ops for missing/corrupt/wrong-version.
 *  - [AppViewModel.acceptReconnect] / [AppViewModel.dismissReconnect] /
 *    [AppViewModel.forgetMarker] behave per their contracts.
 *  - [AppViewModel.connect] / [AppViewModel.connectDemo] persist a marker
 *    on success and not on failure.
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
        rollDeg: Double = 1.25,
        pitchDeg: Double = -0.5,
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
}
