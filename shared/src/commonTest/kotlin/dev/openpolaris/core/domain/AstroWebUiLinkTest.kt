package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiLinkTest {
    @Test
    fun parserPreservesHealthAndSortedOpcodeTelemetry() {
        val result = AstroWebUiLinkParser.parse(
            """{"status":"up","wanted":true,"connects":3,"drops":1,"rx":40,"tx":41,"parse_errors":0,"evictions":2,"up_ms":12000,"last_rx_age_ms":25,"error":"","opcodes":{"286":{"args":"name:K-3;","age_ms":12,"count":4},"518":{"args":"alt:1;","age_ms":8,"count":9}}}""",
        )
        val state = assertIs<AstroWebUiLinkResult.Success>(result).state
        assertEquals(AstroWebUiLinkPhase.UP, state.phase)
        assertEquals(3, state.connects)
        assertEquals(listOf(286, 518), state.opcodes.map { it.command })
        assertEquals("name:K-3;", state.opcodes.first().args)
    }

    @Test
    fun parserAcceptsConnectingAndUnknownLastFrameAge() {
        val result = AstroWebUiLinkParser.parse(
            """{"status":"connecting","wanted":false,"connects":0,"drops":0,"rx":0,"tx":0,"parse_errors":0,"evictions":0,"up_ms":0,"last_rx_age_ms":-1,"error":"sleeping","opcodes":{}}""",
        )
        val state = assertIs<AstroWebUiLinkResult.Success>(result).state
        assertEquals(AstroWebUiLinkPhase.CONNECTING, state.phase)
        assertEquals(-1, state.lastRxAgeMs)
    }

    @Test
    fun parserRejectsMalformedStatusAndOpcode() {
        assertIs<AstroWebUiLinkResult.Failure>(
            AstroWebUiLinkParser.parse("""{"status":"up","wanted":"true","connects":0,"drops":0,"rx":0,"tx":0,"parse_errors":0,"evictions":0,"up_ms":0,"last_rx_age_ms":-1,"error":"","opcodes":{}}"""),
        )
        assertIs<AstroWebUiLinkResult.Failure>(
            AstroWebUiLinkParser.parse("""{"status":"up","wanted":true,"connects":0,"drops":0,"rx":0,"tx":0,"parse_errors":0,"evictions":0,"up_ms":0,"last_rx_age_ms":-1,"error":"","opcodes":{"bad":{}}}"""),
        )
    }
}
