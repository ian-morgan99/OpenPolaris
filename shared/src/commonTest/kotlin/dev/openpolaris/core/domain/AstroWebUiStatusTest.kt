package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiStatusTest {
    @Test
    fun parsesSourceAlignedAstroModeStatusAndIgnoresFutureFields() {
        val result = AstroWebUiStatusParser.parse(
            """{"active":true,"future":"ignored"}""",
        )

        val success = assertIs<AstroWebUiStatusResult.Success>(result)
        assertEquals(true, success.status.active)
    }

    @Test
    fun rejectsMalformedOrIncompleteStatus() {
        assertIs<AstroWebUiStatusResult.Failure>(AstroWebUiStatusParser.parse("{}"))
        assertIs<AstroWebUiStatusResult.Failure>(AstroWebUiStatusParser.parse("not-json"))
    }
}
