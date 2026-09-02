package dev.openpolaris.core.domain

import dev.openpolaris.core.net.SshCommandRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

/**
 * Polls the on-board `/app/Mlog.txt` for the install state
 * machine's terminal lines and reports the outcome of the
 * firmware install.
 *
 * ### Why a separate component
 *
 * The Benro gimbal's `polestar_app` binary, after the on-board
 * `SP_UpgradeCheckFw` watcher picks up `/app/sd/FwPkt.zip`,
 * drives an install state machine and writes progress to
 * `/app/Mlog.txt`. The state strings of interest are
 *
 *  - `OMS_UPGRADE_STA_CHECK_FW PASS`  — every file in the zip
 *     MD5-verified; NAND write will follow
 *  - `SP_EVENT_UPGRADE_SUCCESS` — install completed; reboot imminent
 *  - `OMS_UPGRADE_STA_CHECK_FW FAIL` — one or more file MD5 mismatched;
 *     installer will wipe the staged files and abort
 *  - `SP_EVENT_UPGRADE_FAIL` — installer bailed
 *
 * (See `docs/evidence/gimbal-ssh-2026-08-31/fw-install-flow.txt`
 * for a live transcript of the same strings being emitted
 * during a real install.)
 *
 * Decoupling the watcher from `FirmwareUpdateController` means
 * it can be unit-tested with a fake `SshCommandRunner` and
 * reused by the wire-side path if/when we ever need it.
 *
 * ### How it works
 *
 * On every poll the watcher reads the entire `Mlog.txt` via
 * the injected [SshCommandRunner] and looks for the terminal
 * sentinel *after* the cursor offset. This is more robust
 * than tailing because the Mlog may be rotated mid-install,
 * and reading from a fixed offset guards against that.
 *
 * The watcher does NOT itself manage the cursor — the caller
 * provides one (`MlogCursor.rememberLast()`) that survives
 * across watcher invocations. The unit tests use an in-memory
 * [MlogCursor.InMemory] and a fake `SshCommandRunner` that
 * scripts Mlog growth.
 */
class OnBoardInstallWatcher(
    /** SSH runner used to fetch `/app/Mlog.txt` on each poll. */
    private val ssh: SshCommandRunner,
    /** How often to re-read the Mlog. 500 ms is a good balance
     *  between responsiveness and load. */
    private val pollIntervalMs: Long = 500,
    /** Total time the watcher will keep polling before giving up
     *  with [Outcome.Timeout]. The on-board install runs in
     *  roughly 60–120 s for a 30 MB zip; 5 min is generous. */
    private val overallTimeoutMs: Long = 5 * 60_000L,
    /** Path to the Mlog on the gimbal. Defaults to
     *  `/app/Mlog.txt` per the on-board layout. */
    private val mlogPath: String = "/app/Mlog.txt",
    /** Test seam: clock source. The default uses the real
     *  system clock; tests can pass a function that returns a
     *  controllable virtual time (e.g. the test scope's
     *  `currentTime`) so `delay()` advances virtual time
     *  without sleeping. */
    private val nowMs: () -> Long = SYSTEM_NOW,
    /** Test seam: replaces [kotlinx.coroutines.delay] for unit
     *  tests. Default is the real `delay` (advances virtual
     *  time when running under `runTest`); tests can pass a
     *  no-op or a custom scheduler. */
    private val sleep: suspend (Long) -> Unit = { ms -> delay(ms) },
) {
    /**
     * Outcome of a single watch operation. The watcher stops
     * polling as soon as it produces a terminal result.
     */
    sealed interface Outcome {
        /** `OMS_UPGRADE_STA_CHECK_FW PASS` followed by
         *  `SP_EVENT_UPGRADE_SUCCESS` — install is done; the
         *  gimbal is about to auto-reboot. */
        data object Pass : Outcome

        /** `OMS_UPGRADE_STA_CHECK_FW FAIL` — the staged zip had
         *  a bad MD5. The on-board installer has already wiped
         *  the staging directory; the user must re-upload.
         *  [reason] is the last line emitted before the FAIL. */
        data class Fail(val reason: String) : Outcome

        /** Watcher gave up before seeing a terminal sentinel.
         *  The on-board install is still running (or wedged). */
        data object Timeout : Outcome
    }

    /**
     * Cursor the watcher uses to know which part of the Mlog is
     * "new". The default [MlogCursor.rememberLast] implementation
     * keeps the offset in a JVM-static map keyed by the watcher
     * instance; tests can pass an [MlogCursor.InMemory] instead.
     */
    interface MlogCursor {
        /** Read the next-expected offset. 0 means "from the start". */
        fun read(): Long

        /** Persist the new offset. */
        fun write(offset: Long)

        /** Single-watcher, in-memory cursor. The default for
         *  tests so each test starts from offset 0. */
        class InMemory : MlogCursor {
            @Volatile private var offset: Long = 0
            override fun read(): Long = offset
            override fun write(offset: Long) { this.offset = offset }
        }

        companion object {
            /**
             * Cross-poll cursor that lives in process memory. Good
             * enough for the desktop app; if the process restarts
             * the watcher will simply re-scan the entire Mlog and
             * pick up the same terminal sentinel (idempotent).
             */
            fun rememberLast(): MlogCursor = InMemory()
        }
    }

    /**
     * Watch the Mlog until a terminal sentinel is seen or the
     * overall timeout fires. [cursor] is mutated in place so
     * the next watch invocation continues from where this one
     * stopped.
     *
     * Cancellation: cancelling the calling coroutine aborts the
     * poll loop immediately. The on-board install (if running)
     * continues unaffected.
     */
    suspend fun watch(cursor: MlogCursor = MlogCursor.rememberLast()): Outcome {
        val deadline = nowMs() + overallTimeoutMs
        var lastSeenLine: String = ""
        while (coroutineContext.isActive) {
            val now = nowMs()
            if (now >= deadline) return Outcome.Timeout
            val startOffset = cursor.read()
            val text = readMlogFrom(startOffset)
            if (text.isNotEmpty()) {
                cursor.write(startOffset + text.length)
                val line = text.lineSequence()
                    .lastOrNull { it.isNotBlank() }
                    ?.trim()
                    ?: lastSeenLine
                if (line.isNotEmpty()) lastSeenLine = line
                val term = detectTerminal(text)
                if (term is Terminal.Pass) return Outcome.Pass
                if (term is Terminal.Fail) return Outcome.Fail(term.reason)
            }
            // Wait between polls, but always check the deadline
            // first so a 5-min timeout is honoured even if the
            // poll cadence is sparse.
            val remaining = deadline - nowMs()
            if (remaining <= 0) return Outcome.Timeout
            sleep(minOf(pollIntervalMs, remaining))
        }
        // Coroutine was cancelled mid-loop.
        throw CancellationException("install watcher cancelled while Mlog=$lastSeenLine")
    }

    /**
     * Read Mlog bytes starting at [offset]. Returns the captured
     * text; if the read fails (ssh transport broken) returns an
     * empty string and lets the next poll try again. A persistent
     * transport failure will eventually trip [Outcome.Timeout].
     */
    private fun readMlogFrom(offset: Long): String {
        val cmd = if (offset <= 0) {
            "cat $mlogPath 2>/dev/null"
        } else {
            // `tail -c +N` is 1-indexed: "+1" means "from byte 0".
            // So `+${offset+1}` resumes from the byte after the
            // last one we captured.
            "tail -c +${offset + 1} $mlogPath 2>/dev/null"
        }
        return try {
            val r = ssh.run(cmd)
            if (r.isSuccess) r.stdout else ""
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // SSH transport glitch. Don't fail the watcher
            // outright; the next poll may succeed. The overall
            // timeout will catch sustained outages.
            ""
        }
    }

    /** Result of inspecting the most recent Mlog snippet for a
     *  terminal sentinel. */
    private sealed interface Terminal {
        data object Pass : Terminal
        data class Fail(val reason: String) : Terminal
        data object None : Terminal
    }

    private fun detectTerminal(text: String): Terminal {
        // Walk lines in order; the last terminal-sentinel wins
        // because the state machine emits them in sequence
        // (e.g. CHECK_FW PASS comes before UPGRADE_SUCCESS).
        var result: Terminal = Terminal.None
        for (line in text.lineSequence()) {
            if (PASS_SENTINELS.any { line.contains(it) }) {
                result = Terminal.Pass
            } else if (FAIL_SENTINELS.any { line.contains(it) }) {
                result = Terminal.Fail(line.trim())
            }
        }
        return result
    }

    private fun currentTimeMs(): Long = nowMs()

    companion object {
        // Production clock source. Wraps the JVM-side
        // System.currentTimeMillis(). Tests should always
        // inject their own nowMs() so virtual time advances.
        @JvmStatic
        val SYSTEM_NOW: () -> Long = { SystemMillis.now() }
        /**
         * Substring sentinels that, when seen on the Mlog, mark
         * a successful install. Matched as substrings (not full
         * line) because the Mlog prefixes lines with timestamps
         * and tags that change between firmware versions.
         */
        val PASS_SENTINELS: List<String> = listOf(
            "OMS_UPGRADE_STA_CHECK_FW PASS",
            "SP_EVENT_UPGRADE_SUCCESS",
        )

        /**
         * Substring sentinels that, when seen on the Mlog, mark
         * a failed install. We pair the FAIL with the most
         * recent Mlog line for the [Outcome.Fail.reason] so
         * the UI can surface it to the user.
         */
        val FAIL_SENTINELS: List<String> = listOf(
            "OMS_UPGRADE_STA_CHECK_FW FAIL",
            "SP_EVENT_UPGRADE_FAIL",
        )
    }
}
