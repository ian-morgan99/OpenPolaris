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
    fun parsesSourceSessionFieldsForActiveAstroMode() {
        val result = AstroWebUiStatusParser.parse(
            """{"active":true,"lat":51.5074,"lon":-0.1278,"track_requested":true,"rate":2,"half":false,"align_deg":181.25}""",
        )

        val success = assertIs<AstroWebUiStatusResult.Success>(result)
        assertEquals(true, success.status.active)
        assertEquals(51.5074, success.status.latitudeDeg)
        assertEquals(-0.1278, success.status.longitudeDeg)
        assertEquals(true, success.status.trackRequested)
        assertEquals(2, success.status.rateIndex)
        assertEquals(false, success.status.halfSpeed)
        assertEquals(181.25, success.status.alignmentHeadingDeg)
    }

    @Test
    fun rejectsInvalidOrPartialSessionFields() {
        val invalidLatitude = AstroWebUiStatusParser.parse(
            """{"active":true,"lat":91.0,"lon":0.0}""",
        )
        val partialCoordinates = AstroWebUiStatusParser.parse(
            """{"active":true,"lat":51.5}""",
        )
        val invalidHeading = AstroWebUiStatusParser.parse(
            """{"active":true,"align_deg":360.0}""",
        )
        val invalidRate = AstroWebUiStatusParser.parse(
            """{"active":true,"rate":-1}""",
        )

        assertIs<AstroWebUiStatusResult.Failure>(invalidLatitude)
        assertIs<AstroWebUiStatusResult.Failure>(partialCoordinates)
        assertIs<AstroWebUiStatusResult.Failure>(invalidHeading)
        assertIs<AstroWebUiStatusResult.Failure>(invalidRate)
    }

    @Test
    fun rejectsMalformedOrIncompleteStatus() {
        assertIs<AstroWebUiStatusResult.Failure>(AstroWebUiStatusParser.parse("{}"))
        assertIs<AstroWebUiStatusResult.Failure>(AstroWebUiStatusParser.parse("not-json"))
    }
}
