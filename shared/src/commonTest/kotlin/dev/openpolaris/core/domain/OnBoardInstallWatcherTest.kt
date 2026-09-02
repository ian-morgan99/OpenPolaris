package dev.openpolaris.core.domain

import dev.openpolaris.core.net.SshCommandResult
import dev.openpolaris.core.net.SshCommandRunner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [OnBoardInstallWatcher]. The SSH layer is faked
 * with [FakeSshRunner] which scripts the on-board Mlog
 * contents, and the watcher's clock is replaced with a
 * mutable [FakeClock]. `delay` is also stubbed to a no-op
 * so each watcher loop iteration completes synchronously
 * inside `runTest` virtual time.
 */
class OnBoardInstallWatcherTest {

    /** A trivial [SshCommandRunner] that hands back whatever
     *  scripted stdout the test wants for the next
     *  `tail -c +N /app/Mlog.txt` call. */
    private class FakeSshRunner : SshCommandRunner {
        var mlog: String = ""
        var nextFailure: Throwable? = null
        var calls: Int = 0

        override suspend fun run(command: String): SshCommandResult {
            calls++
            nextFailure?.let { throw it }
            return SshCommandResult(exitCode = 0, stdout = mlog, stderr = "")
        }
    }

    private class FakeClock(var now: Long = 0L) {
        fun advance(ms: Long) { now += ms }
    }

    /** Build a watcher that polls synchronously (no real delay). */
    private fun watcher(
        runner: FakeSshRunner,
        clock: FakeClock,
        overallTimeoutMs: Long = 10_000,
    ): OnBoardInstallWatcher = OnBoardInstallWatcher(
        ssh = runner,
        pollIntervalMs = 0,
        overallTimeoutMs = overallTimeoutMs,
        nowMs = { clock.now },
        sleep = { /* no-op */ },
    )

    @Test
    fun mlogCursor_inMemoryStartsAtZero() {
        val c = OnBoardInstallWatcher.MlogCursor.InMemory()
        assertEquals(0L, c.read())
        c.write(42)
        assertEquals(42L, c.read())
    }

    @Test
    fun watch_returnsPass_whenMlogEmitsCheckFwPassAndUpgradeSuccess() = runTest {
        val runner = FakeSshRunner()
        val clock = FakeClock()
        runner.mlog = "[OMS] OMS_UPGRADE_STA_CHECK_FW PASS some_md5\n"
        val outcome = watcher(runner, clock).watch(OnBoardInstallWatcher.MlogCursor.InMemory())
        assertIs<OnBoardInstallWatcher.Outcome.Pass>(outcome)
    }

    @Test
    fun watch_returnsPass_whenUpgradeSuccessSeenAlone() = runTest {
        val runner = FakeSshRunner()
        val clock = FakeClock()
        runner.mlog = "[OMS] SP_EVENT_UPGRADE_SUCCESS\n"
        val outcome = watcher(runner, clock).watch(OnBoardInstallWatcher.MlogCursor.InMemory())
        assertIs<OnBoardInstallWatcher.Outcome.Pass>(outcome)
    }

    @Test
    fun watch_returnsFail_whenMlogEmitsCheckFwFail() = runTest {
        val runner = FakeSshRunner()
        val clock = FakeClock()
        runner.mlog = "[OMS] OMS_UPGRADE_STA_CHECK_FW FAIL bad_md5\n"
        val outcome = watcher(runner, clock).watch(OnBoardInstallWatcher.MlogCursor.InMemory())
        assertIs<OnBoardInstallWatcher.Outcome.Fail>(outcome)
        assertEquals("[OMS] OMS_UPGRADE_STA_CHECK_FW FAIL bad_md5", outcome.reason)
    }

    @Test
    fun watch_returnsFail_whenUpgradeFailSeen() = runTest {
        val runner = FakeSshRunner()
        val clock = FakeClock()
        runner.mlog = "[OMS] SP_EVENT_UPGRADE_FAIL oops\n"
        val outcome = watcher(runner, clock).watch(OnBoardInstallWatcher.MlogCursor.InMemory())
        assertIs<OnBoardInstallWatcher.Outcome.Fail>(outcome)
    }

    @Test
    fun watch_returnsTimeout_whenDeadlineAlreadyPassed() = runTest {
        val runner = FakeSshRunner()
        val clock = FakeClock()
        // Mlog has no terminal sentinel. The sleep seam
        // advances the clock by 1_000ms per poll; after a
        // few polls the deadline check fires and the
        // watcher returns Timeout.
        runner.mlog = "[OMS] OMS_UPGRADE_STA_CHECKING in_progress\n"
        val outcome = OnBoardInstallWatcher(
            ssh = runner,
            pollIntervalMs = 0,
            overallTimeoutMs = 5_000,
            nowMs = { clock.now },
            sleep = { ms -> clock.advance(ms.coerceAtLeast(1_000)) },
        ).watch(OnBoardInstallWatcher.MlogCursor.InMemory())
        assertIs<OnBoardInstallWatcher.Outcome.Timeout>(outcome)
    }

    @Test
    fun watch_ignoresTransportFailuresUntilTimeout() = runTest {
        val runner = FakeSshRunner()
        val clock = FakeClock()
        // We use a sleep seam that advances the clock by
        // 1_000ms per poll. The deadline check on the next
        // iteration will see now >= deadline and return
        // Timeout. This models the production behaviour
        // where the real clock advances and a persistent
        // transport outage eventually trips the overall
        // timeout.
        runner.nextFailure = java.io.IOException("ssh transport down")
        val outcome = OnBoardInstallWatcher(
            ssh = runner,
            pollIntervalMs = 0,
            overallTimeoutMs = 5_000,
            nowMs = { clock.now },
            sleep = { ms -> clock.advance(ms.coerceAtLeast(1_000)) },
        ).watch(OnBoardInstallWatcher.MlogCursor.InMemory())
        assertIs<OnBoardInstallWatcher.Outcome.Timeout>(outcome)
        // Watcher should have polled at least once before the
        // deadline check fired.
        assertTrue(runner.calls >= 1, "watcher should have polled at least once, calls=${runner.calls}")
    }

    @Test
    fun watch_lastFailWinsWhenBothAppearInSameMlog() = runTest {
        val runner = FakeSshRunner()
        val clock = FakeClock()
        // Edge case: a single poll sees both a PASS and a FAIL
        // (corruption after partial write). The last terminal
        // sentinel wins.
        runner.mlog = "[OMS] OMS_UPGRADE_STA_CHECK_FW PASS\n[OMS] OMS_UPGRADE_STA_CHECK_FW FAIL bad_md5\n"
        val outcome = watcher(runner, clock).watch(OnBoardInstallWatcher.MlogCursor.InMemory())
        assertIs<OnBoardInstallWatcher.Outcome.Fail>(outcome)
        assertTrue(outcome.reason.contains("FAIL bad_md5"), "got reason=${outcome.reason}")
    }

    @Test
    fun watch_advancesCursorByMlogByteLength() = runTest {
        val runner = FakeSshRunner()
        val clock = FakeClock()
        val cursor = OnBoardInstallWatcher.MlogCursor.InMemory()
        runner.mlog = "[OMS] OMS_UPGRADE_STA_CHECK_FW PASS\n[OMS] SP_EVENT_UPGRADE_SUCCESS\n"
        val outcome = watcher(runner, clock).watch(cursor)
        assertIs<OnBoardInstallWatcher.Outcome.Pass>(outcome)
        assertEquals(runner.mlog.length.toLong(), cursor.read())
    }
}
