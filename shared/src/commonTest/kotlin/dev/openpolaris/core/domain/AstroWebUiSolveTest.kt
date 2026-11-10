package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiSolveTest {
    @Test
    fun parsesSourceTargetAndFrameRecordsWhileIgnoringFutureFields() {
        val result = AstroWebUiSolveParser.parse(
            """{"target":{"session_id":"s1","target_ra_deg":12.5,"target_dec_deg":-4.25,"updated":"2026-11-10T00:00:00Z","future":1},"last_frame":{"solved":true,"ra_deg":12.6,"dec_deg":-4.2,"rotation_deg":1.5,"matched_count":24,"rms_px":0.8,"logodds":42.0,"future":true}}""",
        )
        val success = assertIs<AstroWebUiSolveResult.Success>(result)
        assertEquals("s1", success.state.target?.sessionId)
        assertEquals(12.5, success.state.target?.targetRightAscensionDeg)
        assertEquals(-4.25, success.state.target?.targetDeclinationDeg)
        assertEquals(true, success.state.lastFrame?.solved)
        assertEquals(12.6, success.state.lastFrame?.rightAscensionDeg)
        assertEquals(24, success.state.lastFrame?.matchedCount)
    }

    @Test
    fun acceptsEmptySourceStateAndExplicitNulls() {
        val result = assertIs<AstroWebUiSolveResult.Success>(
            AstroWebUiSolveParser.parse("""{"target":null,"last_frame":null}"""),
        )
        assertEquals(null, result.state.target)
        assertEquals(null, result.state.lastFrame)
    }

    @Test
    fun rejectsMalformedShapesAndUnsafeCoordinates() {
        val cases = listOf(
            """{"target":[]}""",
            """{"last_frame":{"ra_deg":361.0,"dec_deg":0.0}}""",
            """{"last_frame":{"ra_deg":1.0}}""",
            """{"last_frame":{"solved":true,"dec_deg":91.0,"ra_deg":1.0}}""",
            """{"last_frame":{"drift_deg":-1.0}}""",
            """{"last_frame":{"nmatch":-1}}""",
        )
        cases.forEach { body ->
            assertIs<AstroWebUiSolveResult.Failure>(AstroWebUiSolveParser.parse(body))
        }
    }

    @Test
    fun acceptsWatcherFailureAndActionMetadataWithoutClaimingAResult() {
        val result = assertIs<AstroWebUiSolveResult.Success>(
            AstroWebUiSolveParser.parse(
                """{"target":{"target_ra_deg":1.0,"target_dec_deg":2.0},"last_frame":{"solved":false,"failure_reason":"solver_no_output","action":"no_target"}}""",
            ),
        )
        assertEquals(false, result.state.lastFrame?.solved)
        assertEquals("solver_no_output", result.state.lastFrame?.failureReason)
        assertEquals("no_target", result.state.lastFrame?.action)
    }
}
