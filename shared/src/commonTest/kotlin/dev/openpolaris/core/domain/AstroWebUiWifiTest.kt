package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiWifiTest {
    @Test
    fun parserPreservesInterfaceAndConfigurationState() {
        val state = assertIs<AstroWebUiWifiResult.Success>(
            AstroWebUiWifiParser.parse(
                """{"ok":true,"ap_ip":"192.168.0.1","sta_ip":"10.0.0.4","ssid":"Polaris_1234","configured":true,"autojoin":false}""",
            ),
        ).state
        assertEquals("192.168.0.1", state.apIp)
        assertEquals("10.0.0.4", state.stationIp)
        assertEquals("Polaris_1234", state.ssid)
        assertEquals(true, state.configured)
        assertEquals(false, state.autojoin)
    }

    @Test
    fun parserAcceptsEmptyUnconfiguredValues() {
        val state = assertIs<AstroWebUiWifiResult.Success>(
            AstroWebUiWifiParser.parse(
                """{"ok":true,"ap_ip":"","sta_ip":"","ssid":"","configured":false,"autojoin":false}""",
            ),
        ).state
        assertEquals("", state.apIp)
        assertEquals(false, state.configured)
    }

    @Test
    fun parserRejectsOverlongSsid() {
        assertIs<AstroWebUiWifiResult.Failure>(
            AstroWebUiWifiParser.parse(
                """{"ok":true,"ap_ip":"","sta_ip":"","ssid":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx","configured":false,"autojoin":false}""",
            ),
        )
    }
}
