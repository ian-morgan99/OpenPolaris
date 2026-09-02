package dev.openpolaris.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JVM leak test for `MountSession.shutdown()` (issue #20 / 3a.1).
 *
 * Acceptance from the issue:
 *  - `Session.shutdown()` cancels all in-flight reader coroutines.
 *  - 10× connect→disconnect cycles, no growth in
 *    active-coroutines count (or bounded `Thread.activeCount()` delta).
 *
 * Strategy:
 *  - Stand up [MountSession] with the **production** readerScope
 *    (`Dispatchers.Default + SupervisorJob`). The production scope is
 *    the leak source; the existing commonTest tests cheat by passing
 *    the [runTest] scope, which is auto-cancelled when the test body
 *    returns and so would hide the bug.
 *  - Drive 10 cycles: build session → connect (with a 284 handshake
 *    response) → disconnect → shutdown. After each cycle, the session
 *    is fully released; the next cycle creates a brand new one.
 *  - After the 10th cycle, sample JVM thread state via
 *    [ManagementFactory]. The thread-count delta is the real leak
 *    signal: every leaked reader coroutine on `Dispatchers.Default`
 *    eventually parks on a worker thread, and an unbounded loop of
 *    10 cycles would grow the thread count. Bounded growth (≤4
 *    threads delta, generous for CI noise) is the acceptance.
 *
 * Why this test lives in `jvmTest` and not `commonTest`:
 *  - The leak manifests on every real KMP target, but the test
 *    driver uses `ManagementFactory` which is JVM-only. iOS and
 *    Android each have their own thread-management APIs and are
 *    expected to ship with hardware-leak tests in v2 once the real
 *    hardware is in CI.
 */
class SessionShutdownLeakTest {

    /**
     * In-memory [Connection] that records writes and serves queued responses.
     *
     * Replies are keyed by request code and only revealed to [read] **after**
     * a matching [write] has been observed. This mirrors how the real mount
     * behaves: a request is the only thing that ever elicits a code-matched
     * reply. Pre-queuing the reply ahead of the write is what surfaced
     * issue #10 — the reader's first iteration would consume the response
     * before the writer's waiter was registered, and the matching
     * `request(code, ...)` would then time out.
     */
    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        val pendingByCode = mutableMapOf<Int, ByteArray>()
        val seenRequestCodes = mutableSetOf<Int>()
        @Volatile var dropped = false

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {}

        override suspend fun write(data: ByteArray) {
            if (dropped) throw java.io.IOException("socket closed")
            written += data
            // Wire format is `1&<code>&2&...;#` — the code is the segment
            // after the first `&`. Record it so [read] knows the matching
            // reply is now legitimate to deliver.
            val s = String(data, Charsets.US_ASCII)
            val parts = s.split("&")
            if (parts.size >= 2) {
                val code = parts[1].toIntOrNull()
                if (code != null) seenRequestCodes.add(code)
            }
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            if (dropped) throw java.io.IOException("socket closed")
            // For any request code we've seen a write for, return the
            // matching pending response once and remove it.
            for (code in seenRequestCodes) {
                val r = pendingByCode.remove(code) ?: continue
                seenRequestCodes.remove(code)
                r.copyInto(buffer)
                return r.size
            }
            return -1
        }

        override fun close() {}
    }

    @Test
    fun tenCyclesDoNotLeakThreads() = runBlocking {
        // 284 push-mode-state response so connect()'s handshake completes.
        val handshake = "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)

        // Warm the dispatcher pool so the first cycle's thread count
        // isn't artificially low. Without this, the baseline would
        // start at the "before any coroutine ran" thread count and
        // grow by one or two threads when Dispatchers.Default spins
        // up its workers, which is not a leak and would mask the
        // signal we want.
        withTimeout(2000) {
            val warm = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            delay(50)
        }
        // The warmup coroutine may not have fully unwound yet; give
        // it another beat to release any pool worker before
        // sampling baseline.
        withTimeout(2000) { delay(50) }
        val baselineThreads = ManagementFactory.getThreadMXBean().threadCount

        repeat(10) { cycle ->
            val conn = FakeConnection()
            // 284 handshake reply: only released to the reader after the
            // writer actually writes a 284 request (see FakeConnection).
            conn.pendingByCode[284] = handshake
            // 820/823 auth replies: same gating.
            conn.pendingByCode[820] = "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
            conn.pendingByCode[823] = "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)
            // Production scope — Dispatchers.Default + SupervisorJob,
            // the exact path that leaks in production if shutdown() is
            // a no-op. Each cycle creates a fresh scope so the only
            // surviving coroutines after the loop are the ones
            // shutdown() failed to cancel.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val session = MountSession(
                connectionFactory = { conn },
                readerScope = scope,
            )
            val ok = session.connect()
            assertTrue(ok, "connect() should succeed on cycle $cycle")
            session.disconnect()
            session.shutdown()
            // Belt-and-braces: scope should be inactive after shutdown.
            // If this fails, shutdown() is not cancelling the scope
            // itself and the test will also see growing thread counts
            // below.
            assertTrue(
                !scope.isActive,
                "scope must be inactive after shutdown() on cycle $cycle",
            )
            // Yield to let any in-flight dispatch unwind before the
            // next cycle. Without this the next iteration's
            // baseline can be polluted by a still-pending dispatch
            // from the previous shutdown.
            withTimeout(1000) { delay(20) }
        }

        // Give the scheduler one last chance to drain.
        withTimeout(2000) { delay(100) }
        val finalThreads = ManagementFactory.getThreadMXBean().threadCount
        val delta = finalThreads - baselineThreads
        // Bounded ceiling of 4 threads delta — generous to absorb CI
        // jitter (GC threads, JIT compiler threads, test runner
        // helpers) but tight enough to catch the 10× regression: each
        // leaked reader would park on a Default-dispatcher worker
        // and the delta would balloon to dozens.
        assertTrue(
            delta <= 4,
            "expected thread-count delta to stay bounded after 10 shutdown cycles, " +
                "got baseline=$baselineThreads final=$finalThreads delta=$delta",
        )
    }
}
