package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiLimitsTest {
    @Test
    fun parserPreservesEnforcedAndReleasedStates() {
        assertEquals(
            false,
            assertIs<AstroWebUiLimitsResult.Success>(
                AstroWebUiLimitsParser.parse("""{"ok":true,"released":false}"""),
            ).state.released,
        )
        assertEquals(
            true,
            assertIs<AstroWebUiLimitsResult.Success>(
                AstroWebUiLimitsParser.parse("""{"ok":true,"released":true}"""),
            ).state.released,
        )
    }

    @Test
    fun parserKeepsUnknownLimitStateDistinct() {
        assertEquals(
            null,
            assertIs<AstroWebUiLimitsResult.Success>(
                AstroWebUiLimitsParser.parse("""{"ok":true,"released":null}"""),
            ).state.released,
        )
    }

    @Test
    fun parserRejectsStringOrMissingAcknowledgement() {
        assertIs<AstroWebUiLimitsResult.Failure>(
            AstroWebUiLimitsParser.parse("""{"ok":true,"released":"false"}"""),
        )
        assertIs<AstroWebUiLimitsResult.Failure>(
            AstroWebUiLimitsParser.parse("""{"released":false}"""),
        )
    }
}
