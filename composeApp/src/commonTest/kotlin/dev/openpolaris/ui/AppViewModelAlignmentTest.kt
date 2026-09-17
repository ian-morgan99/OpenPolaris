package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.GimbalPosition
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the multi-star alignment `num` index defect
 * (docs/ASTRO-WORKFLOW-HANDOVER-2026-09-17.md §Critical defects #1–#3).
 *
 * The defect: [AppViewModel.submitAlignmentStar] constructed a fresh
 * [dev.openpolaris.core.domain.AlignmentController] on every call, so the
 * firmware always received `num:0` regardless of how many stars had been
 * recorded. The UI counter (`alignmentStars`) incremented independently and
 * displayed 1, 2, 3 while the wire payload stayed at `num:0`.
 *
 * These tests assert the exact outgoing code-530 sequence `num:0`, `num:1`,
 * `num:2` so a regression to per-call controller construction is caught.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAlignmentTest {

    /**
     * In-memory [Connection] that records every write for assertion and
     * enqueues handshake responses on demand (the `onWrite` pattern from
     * [AppViewModelSolveNowTest]): pre-queued frames can be consumed by the
     * reader before a request's waiter is registered, so responses must be
     * enqueued as the matching request is written.
     */
    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        private val channel = kotlinx.coroutines.channels.Channel<ByteArray>(
            kotlinx.coroutines.channels.Channel.BUFFERED,
        )

        fun enqueueResponse(data: ByteArray) { channel.trySend(data) }

        /** Hook invoked from [write]; tests script responses per request code. */
        var onWrite: ((ByteArray) -> Unit)? = null

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {}

        override suspend fun write(data: ByteArray) {
            written += data
            onWrite?.invoke(data)
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val r = channel.receive()
            r.copyInto(buffer)
            return r.size
        }

        override fun close() { channel.close() }
    }

    private fun newViewModel(scope: CoroutineScope, conn: Connection): AppViewModel =
        AppViewModel(
            scope = scope,
            connectionFactory = { conn },
        )

    /**
     * Install a [MountSession] on the VM without going through [AppViewModel.connect]
     * (which would also start poll loops and preview fetch). Mirrors the pattern
     * in [AppViewModelSolveNowTest.installSession].
     */
    private suspend fun installSession(vm: AppViewModel, conn: Connection, scope: CoroutineScope) {
        // Enqueue handshake responses as the matching request is written —
        // see the onWrite pattern in AppViewModelSolveNowTest.
        (conn as FakeConnection).onWrite = { req ->
            val s = String(req, Charsets.US_ASCII)
            when {
                s.startsWith("1&284&") -> conn.enqueueResponse(
                    "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII),
                )
                s.startsWith("1&820&") -> conn.enqueueResponse(
                    "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII),
                )
                s.startsWith("1&823&") -> conn.enqueueResponse(
                    "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII),
                )
            }
        }

        val s = MountSession(
            connectionFactory = { conn },
            host = "127.0.0.1",
            port = 9090,
            readerScope = scope,
        )
        s.connect()
        vm.testInstallSession(s)
    }

    /** Extract the `num` field from a code-530 frame's payload. */
    private fun extractNum(frame: ByteArray): Int? {
        val text = String(frame, Charsets.US_ASCII)
        if (!text.contains("&530&")) return null
        // Payload format: step:2;yaw:...;pitch:...;lat:...;num:N;lng:...;
        val match = Regex("num:(\\d+)").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    @Test
    fun `three alignment submissions produce num 0 1 2`() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this, conn)
        try {
            installSession(vm, conn, this)
            advanceUntilIdle()

            // Set up the prerequisites that submitAlignmentStar() checks.
            vm.testSetPosition(GimbalPosition(yaw = 180f, pitch = 45f, roll = 0f))
            vm.updateLat("40.0")
            vm.updateLng("0.0")

            // Submit three alignment stars.
            vm.submitAlignmentStar()
            advanceUntilIdle()
            vm.submitAlignmentStar()
            advanceUntilIdle()
            vm.submitAlignmentStar()
            advanceUntilIdle()

            // Find all code-530 frames in the written bytes.
            val numFrames = conn.written.filter { String(it, Charsets.US_ASCII).contains("&530&") }
            assertEquals(3, numFrames.size, "expected 3 code-530 frames, got ${numFrames.size}")

            // The critical assertion: each frame must carry a distinct, sequential index.
            val nums = numFrames.map { extractNum(it) }
            assertEquals(listOf(0, 1, 2), nums, "alignment star indices must be 0, 1, 2")

            // The UI counter should agree.
            assertEquals(3, vm.alignmentStars)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun `reset alignment clears the persistent controller`() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this, conn)
        try {
            installSession(vm, conn, this)
            advanceUntilIdle()

            vm.testSetPosition(GimbalPosition(yaw = 180f, pitch = 45f, roll = 0f))
            vm.updateLat("40.0")
            vm.updateLng("0.0")

            // Record two stars, then reset, then record one more.
            vm.submitAlignmentStar()
            advanceUntilIdle()
            vm.submitAlignmentStar()
            advanceUntilIdle()
            assertEquals(2, vm.alignmentStars)

            vm.resetAlignment()
            advanceUntilIdle()
            assertEquals(0, vm.alignmentStars)

            // After reset the next submission must start at num:0 again.
            vm.submitAlignmentStar()
            advanceUntilIdle()

            val all530 = conn.written.filter { String(it, Charsets.US_ASCII).contains("&530&") }
            assertEquals(3, all530.size)
            // The third frame (post-reset) must carry num:0.
            assertEquals(0, extractNum(all530[2]))
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun `submit without position is rejected`() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = newViewModel(this, conn)
        try {
            installSession(vm, conn, this)
            advanceUntilIdle()

            // No position set yet.
            vm.testSetPosition(null)
            vm.updateLat("40.0")
            vm.updateLng("0.0")

            vm.submitAlignmentStar()
            advanceUntilIdle()

            assertTrue(
                vm.statusMessage.contains("No mount position"),
                "expected 'No mount position' in status, got '${vm.statusMessage}'",
            )
            assertEquals(0, vm.alignmentStars)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun `submit without session is rejected`() = runTest(UnconfinedTestDispatcher()) {
        val vm = newViewModel(this, FakeConnection())
        try {
            vm.testSetPosition(GimbalPosition(yaw = 180f, pitch = 45f, roll = 0f))
            vm.updateLat("40.0")
            vm.updateLng("0.0")

            vm.submitAlignmentStar()
            advanceUntilIdle()

            assertEquals("Not connected", vm.statusMessage)
            assertEquals(0, vm.alignmentStars)
        } finally {
            vm.preview.shutdown()
        }
    }
}
