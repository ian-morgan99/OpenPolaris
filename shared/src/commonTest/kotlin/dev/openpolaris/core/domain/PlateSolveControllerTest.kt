package dev.openpolaris.core.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [PlateSolveController] using a hand-rolled fake transport so we
 * can drive the pipeline deterministically without hitting nova.astrometry.net.
 *
 * Contract under test:
 *  - [PlateSolveController.solve] emits a sequence of [PlateSolveStatus]s
 *    in the documented order: LoggingIn → Uploading → PollingSubmission →
 *    PollingJob → Solved(result).
 *  - Each transport failure is converted to a Failed status and re-thrown.
 *  - Transient transport failures (null from submissionJobs/jobStatus) do
 *    NOT abort the pipeline — we retry until pollAttempts.
 *  - A transport that never returns a non-empty jobs list surfaces as a
 *    clear "no jobs after N attempts" error.
 *  - A job status of "failure" (without any "success" job in the
 *    submission) raises.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlateSolveControllerTest {

    /** Hand-rolled fake: every method's behaviour is dictated by the
     *  test. We track calls so the test can assert on the sequence. */
    private class FakeTransport : PlateSolveTransport {
        val calls = mutableListOf<String>()
        var loginResult: String = "test-session"
        var uploadResult: Long = 42L
        var submissionJobsResult: List<Long> = listOf(7L)
        var jobStatusResult: String = "success"
        var jobCalibrationResult: PlateSolveResult = PlateSolveResult(
            raDeg = 123.456,
            decDeg = -45.678,
            pixscaleArcsec = 1.5,
            orientationDeg = 12.0,
            parity = 1,
            jobId = 7L,
        )

        // Hooks for failure injection.
        var throwOnLogin: Throwable? = null
        var throwOnUpload: Throwable? = null
        var submissionJobsSequence: MutableList<List<Long>> = mutableListOf()
        var jobStatusSequence: MutableList<String> = mutableListOf()
        var throwOnCalibration: Throwable? = null

        override fun login(host: String, apiKey: String?): String {
            calls += "login"
            throwOnLogin?.let { throw it }
            return loginResult
        }
        override fun upload(host: String, session: String, requestJson: String, imageBytes: ByteArray): Long {
            calls += "upload(${imageBytes.size})"
            throwOnUpload?.let { throw it }
            return uploadResult
        }
        override fun submissionReady(host: String, session: String, subId: Long): Boolean {
            return submissionJobsResult.isNotEmpty()
        }
        override fun submissionJobs(host: String, session: String, subId: Long): List<Long> {
            calls += "submissionJobs($subId)"
            // Pop from the scripted sequence; if empty, return the
            // canned result.
            if (submissionJobsSequence.isNotEmpty()) {
                return submissionJobsSequence.removeAt(0)
            }
            return submissionJobsResult
        }
        override fun jobStatus(host: String, session: String, jobId: Long): String {
            calls += "jobStatus($jobId)"
            if (jobStatusSequence.isNotEmpty()) {
                return jobStatusSequence.removeAt(0)
            }
            return jobStatusResult
        }
        override fun jobCalibration(host: String, session: String, jobId: Long): PlateSolveResult {
            calls += "jobCalibration($jobId)"
            throwOnCalibration?.let { throw it }
            return jobCalibrationResult
        }
    }

    @Test
    fun happyPathEmitsFullStatusSequence() = runTest {
        val transport = FakeTransport()
        val statusLog = mutableListOf<PlateSolveStatus>()
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 5,
            pollDelayMs = 1L, // don't sleep in tests
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = ctrl.solve(
            imageBytes = ByteArray(128) { it.toByte() },
            onStatus = { statusLog += it },
        )

        // Final result
        assertEquals(7L, result.jobId)
        assertEquals(123.456, result.raDeg)
        assertEquals(-45.678, result.decDeg)
        assertEquals(1.5, result.pixscaleArcsec)

        // Status sequence
        assertIs<PlateSolveStatus.LoggingIn>(statusLog.first())
        assertIs<PlateSolveStatus.Uploading>(statusLog[1])
        val solved = statusLog.last()
        assertIs<PlateSolveStatus.Solved>(solved)
        assertEquals(7L, solved.result.jobId)

        // All transport calls fired in the right order
        assertEquals(listOf("login", "upload(128)", "submissionJobs(42)", "jobStatus(7)", "jobCalibration(7)"), transport.calls)
    }

    @Test
    fun loginFailureMarksFailedAndRethrows() = runTest {
        val transport = FakeTransport().apply { throwOnLogin = RuntimeException("no network") }
        val statusLog = mutableListOf<PlateSolveStatus>()
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 1,
            pollDelayMs = 1L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val ex = assertFailsWith<RuntimeException> {
            ctrl.solve(ByteArray(4), onStatus = { statusLog += it })
        }
        assertEquals("no network", ex.message)

        val failed = statusLog.last()
        assertIs<PlateSolveStatus.Failed>(failed)
        assertEquals("no network", failed.reason)
        // No upload should have happened.
        assertEquals(listOf("login"), transport.calls)
    }

    @Test
    fun uploadFailureMarksFailedAndRethrows() = runTest {
        val transport = FakeTransport().apply { throwOnUpload = RuntimeException("413 too large") }
        val statusLog = mutableListOf<PlateSolveStatus>()
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 1,
            pollDelayMs = 1L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val ex = assertFailsWith<RuntimeException> {
            ctrl.solve(ByteArray(4), onStatus = { statusLog += it })
        }
        assertEquals("413 too large", ex.message)

        val failed = statusLog.last()
        assertIs<PlateSolveStatus.Failed>(failed)
        assertEquals(listOf("login", "upload(4)"), transport.calls)
    }

    @Test
    fun noJobsAfterMaxAttemptsSurfacesAsFailure() = runTest {
        val transport = FakeTransport().apply {
            // Always return empty → never get any job ids.
            submissionJobsResult = emptyList()
        }
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 3,
            pollDelayMs = 1L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val ex = assertFailsWith<IllegalStateException> {
            ctrl.solve(ByteArray(4))
        }
        assertTrue(ex.message!!.contains("submission 42 produced no jobs"))
        // Three polling attempts, each with a submissionJobs call.
        assertEquals(3, transport.calls.count { it.startsWith("submissionJobs") })
    }

    @Test
    fun jobFailureSurfacesAsFailure() = runTest {
        val transport = FakeTransport().apply {
            // Submission yields one job that finishes with "failure".
            submissionJobsResult = listOf(7L)
            jobStatusResult = "failure"
        }
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 3,
            pollDelayMs = 1L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val ex = assertFailsWith<IllegalStateException> {
            ctrl.solve(ByteArray(4))
        }
        assertTrue(ex.message!!.contains("none of the submission's jobs succeeded"))
        // Calibration should never have been called.
        assertEquals(0, transport.calls.count { it.startsWith("jobCalibration") })
    }

    @Test
    fun transientSubmissionJobsFailureRetriesUntilJobsAppear() = runTest {
        val transport = FakeTransport().apply {
            // First two attempts return empty (treated as transient), then
            // the canned (non-empty) result kicks in.
            submissionJobsSequence.add(emptyList())
            submissionJobsSequence.add(emptyList())
            // submissionJobsResult is the default: listOf(7L)
        }
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 10,
            pollDelayMs = 1L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = ctrl.solve(ByteArray(4))
        assertEquals(7L, result.jobId)
        // Three submissionJobs calls — two empty + one non-empty.
        val jobsCalls = transport.calls.filter { it.startsWith("submissionJobs") }
        assertEquals(3, jobsCalls.size)
    }

    @Test
    fun transientJobStatusRetriesUntilSuccess() = runTest {
        val transport = FakeTransport().apply {
            // First two attempts return "pending", then "success".
            jobStatusSequence.add("pending")
            jobStatusSequence.add("pending")
            // jobStatusResult default is "success"
        }
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 10,
            pollDelayMs = 1L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = ctrl.solve(ByteArray(4))
        assertEquals(7L, result.jobId)
        val statusCalls = transport.calls.filter { it.startsWith("jobStatus") }
        assertEquals(3, statusCalls.size)
    }

    @Test
    fun calibrationFailureSurfacesAsFailure() = runTest {
        val transport = FakeTransport().apply {
            throwOnCalibration = RuntimeException("calibration read failed")
        }
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 2,
            pollDelayMs = 1L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val ex = assertFailsWith<RuntimeException> {
            ctrl.solve(ByteArray(4))
        }
        assertEquals("calibration read failed", ex.message)
    }

    @Test
    fun cancelsCleanlyBetweenPolls() = runTest {
        // If the caller cancels, we should NOT emit a Failed status — the
        // exception is a CancellationException, which propagates.
        val transport = FakeTransport().apply {
            // Never yield jobs.
            submissionJobsResult = emptyList()
        }
        val statusLog = mutableListOf<PlateSolveStatus>()
        val ctrl = PlateSolveController(
            transport = transport,
            pollAttempts = 100,
            pollDelayMs = 1L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Run the solve in a child coroutine, then cancel it from the
        // outside so the pipeline is interrupted mid-poll.
        val job = backgroundScope.launch {
            ctrl.solve(ByteArray(4), onStatus = { statusLog += it })
        }
        // Yield once so we get at least one status emission, then cancel.
        runCurrent()
        job.cancel()
        job.join()

        // The very last status observed should be a PollingSubmission, NOT
        // Failed. (CancellationException bypasses our catch-and-Failed
        // branch — the status list is captured up to the moment of cancel.)
        assertTrue(statusLog.isNotEmpty(), "expected at least one status before cancel")
        assertTrue(
            statusLog.last() is PlateSolveStatus.PollingSubmission,
            "expected last status to be PollingSubmission, was ${statusLog.last()}",
        )
    }
}
