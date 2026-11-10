package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiProgrammeTest {
    @Test
    fun parsesRunningPanoramaAndIgnoresFutureFields() {
        val result = AstroWebUiProgrammeParser.parse(
            """{"kind":"panorama","elapsed_s":12,"total":20,"remaining":8,"unlimited":false,"paused":true,"preview":false,"stale":false,"taken":12,"cols":4,"rows":5,"per_spot":1,"interval_s":3,"future":1}""",
        )

        val success = assertIs<AstroWebUiProgrammeResult.Success>(result)
        assertEquals("panorama", success.programme.kind)
        assertEquals(12.0, success.programme.elapsedSeconds)
        assertEquals(12L, success.programme.taken)
        assertEquals(4, success.programme.columns)
        assertEquals(true, success.programme.paused)
    }

    @Test
    fun parsesIdleProgramme() {
        val result = AstroWebUiProgrammeParser.parse("""{"kind":"none"}""")

        val success = assertIs<AstroWebUiProgrammeResult.Success>(result)
        assertEquals("none", success.programme.kind)
        assertEquals(null, success.programme.total)
    }

    @Test
    fun rejectsInvalidProgressValues() {
        assertIs<AstroWebUiProgrammeResult.Failure>(
            AstroWebUiProgrammeParser.parse("""{"kind":"timelapse","total":-2}"""),
        )
        assertIs<AstroWebUiProgrammeResult.Failure>(
            AstroWebUiProgrammeParser.parse("""{"kind":"panorama","remaining":-3}"""),
        )
        assertIs<AstroWebUiProgrammeResult.Failure>(
            AstroWebUiProgrammeParser.parse("""{"kind":"panorama","elapsed_s":-1}"""),
        )
    }
}
