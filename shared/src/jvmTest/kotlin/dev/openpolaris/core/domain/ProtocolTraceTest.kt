package dev.openpolaris.core.domain

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The [ProtocolTrace] gate is lazy-initialised from a system property or
 * env var, so we can't reset it mid-test cheaply. Each test sets the
 * property at the top and clears it afterwards, accepting that the very
 * first read wins for the lifetime of the JVM.
 *
 * These tests use distinct property keys (`openpolaris.test.trace.X`) that
 * the [ProtocolTrace] code path looks for — we re-use the production key
 * because the gate is intentionally simple and a single boolean.
 */
class ProtocolTraceTest {
    @AfterTest
    fun cleanup() {
        System.clearProperty("openpolaris.protocol.trace")
    }

    @Test
    fun gateIsOffByDefault() {
        // Already covered by every other test in the suite running with
        // the property unset, but pinned here so a future maintainer
        // doesn't accidentally enable the trace globally.
        System.clearProperty("openpolaris.protocol.trace")
        assertFalse(ProtocolTrace.isOn)
    }

    @Test
    fun gateRespondsToSystemProperty() {
        System.setProperty("openpolaris.protocol.trace", "true")
        // isOn is lazy — first read after the set takes effect.
        assertTrue(ProtocolTrace.isOn)
    }
}
