package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiRunStateTest {
    @Test
    fun parsesSourceRunStateAndOptionalSolution() {
        val result = assertIs<AstroWebUiRunStateResult.Success>(
            AstroWebUiRunStateParser.parse(
                """{"status":"tracking","elapsed_sec":123,"mount_read":false,"mount_blocked":false,"alt":42.5,"az":359.9,"aligned":true,"tracking":true,"solution":{"solved":true,"ra_deg":12.5,"dec_deg":-4.25},"log":"ok","future":1}""",
            ),
        )
        assertEquals("tracking", result.state.status)
        assertEquals(123L, result.state.elapsedSeconds)
        assertEquals(42.5, result.state.altitudeDeg)
        assertEquals(359.9, result.state.azimuthDeg)
        assertEquals(true, result.state.aligned)
        assertEquals(true, result.state.tracking)
        assertEquals(12.5, result.state.solution?.rightAscensionDeg)
    }

    @Test
    fun preservesExplicitBlockedMountRead() {
        val result = assertIs<AstroWebUiRunStateResult.Success>(
            AstroWebUiRunStateParser.parse(
                """{"status":"aligned","elapsed_sec":0,"mount_read":false,"mount_blocked":true,"alt":0,"az":0,"aligned":true,"tracking":false,"solution":null}""",
            ),
        )
        assertEquals(false, result.state.mountRead)
        assertEquals(true, result.state.mountBlocked)
    }

    @Test
    fun rejectsMalformedShapesAndUnsafeRanges() {
        val cases = listOf(
            """{"status":"ok","elapsed_sec":1,"mount_read":true,"mount_blocked":true,"alt":0,"az":0,"aligned":false,"tracking":false}""",
            """{"status":"ok","elapsed_sec":1,"mount_read":false,"mount_blocked":false,"alt":91,"az":0,"aligned":false,"tracking":false}""",
            """{"status":"ok","elapsed_sec":1,"mount_read":false,"mount_blocked":false,"alt":0,"az":361,"aligned":false,"tracking":false}""",
            """{"status":"ok","elapsed_sec":1,"mount_read":false,"mount_blocked":false,"alt":0,"az":0,"aligned":false,"tracking":false,"solution":{"ra_deg":1}}""",
            """{"status":"ok","elapsed_sec":1,"mount_read":false,"mount_blocked":false,"alt":0,"az":0,"aligned":false,"tracking":false,"solution":{"ra_deg":360,"dec_deg":0}}""",
            """{"status":"ok","elapsed_sec":1,"mount_read":false,"mount_blocked":false,"alt":0,"az":0,"aligned":false,"tracking":false,"log":true}""",
        )
        cases.forEach { body ->
            assertIs<AstroWebUiRunStateResult.Failure>(AstroWebUiRunStateParser.parse(body))
        }
    }

    @Test
    fun requiresReadOnlyStateFields() {
        val result = AstroWebUiRunStateParser.parse(
            """{"status":"ok","elapsed_sec":1,"mount_read":false,"mount_blocked":false,"alt":0,"az":0,"aligned":false}""",
        )
        assertIs<AstroWebUiRunStateResult.Failure>(result)
    }
}
