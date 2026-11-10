package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiSiteTest {
    @Test
    fun parsesSavedSiteAndIgnoresFutureFields() {
        val result = AstroWebUiSiteParser.parse(
            """{"have_pos":true,"lat":51.5074,"lon":-0.1278,"future":1}""",
        )

        val success = assertIs<AstroWebUiSiteResult.Success>(result)
        assertEquals(true, success.site.havePosition)
        assertEquals(51.5074, success.site.latitudeDeg)
        assertEquals(-0.1278, success.site.longitudeDeg)
    }

    @Test
    fun noSavedSiteIsAValidReadOnlyState() {
        val result = AstroWebUiSiteParser.parse("""{"have_pos":false}""")

        val success = assertIs<AstroWebUiSiteResult.Success>(result)
        assertEquals(false, success.site.havePosition)
        assertEquals(null, success.site.latitudeDeg)
        assertEquals(null, success.site.longitudeDeg)
    }

    @Test
    fun rejectsMissingOrOutOfRangeCoordinatesWhenSiteIsPresent() {
        assertIs<AstroWebUiSiteResult.Failure>(AstroWebUiSiteParser.parse("""{"have_pos":true}"""))
        assertIs<AstroWebUiSiteResult.Failure>(AstroWebUiSiteParser.parse("""{"have_pos":true,"lat":91,"lon":0}"""))
        assertIs<AstroWebUiSiteResult.Failure>(AstroWebUiSiteParser.parse("""{"have_pos":true,"lat":0,"lon":181}"""))
    }
}
