package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlowMotionCapabilityManifestTest {
    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun manifest(extra: String = ""): String = """
        {
          "schema": 1,
          "capability": "openpolaris.slow-motion",
          "firmware_identity": "fw-build-2026-11-11",
          "gimbal_identity": "polaris-k3iii-m1",
          "source_app_identity": "benro-connect-android-1.4.0",
          "evidence_bundle_id": "slow-qual-0001",
          "evidence_bundle_sha256": "$sha",
          "route_qualified": true,
          "axis_signs_qualified": true,
          "rate_envelope_qualified": true,
          "acknowledgement_qualified": true,
          "watchdog_qualified": true,
          "stop_qualified": true,
          "disconnect_recovery_qualified": true,
          "limits_qualified": true$extra
        }
    """.trimIndent()

    @Test
    fun validManifestConvertsToACompleteRecord() {
        val result = SlowMotionCapabilityManifest.decode(manifest())
        assertTrue(result.isSuccess)
        val record = result.getOrThrow().toCapabilityRecord()
        assertTrue(record.liveReady)
        assertEquals(emptyList(), record.blockers)
    }

    @Test
    fun partialEvidenceRemainsNotReadyAndNamesTheMissingGate() {
        val result = SlowMotionCapabilityManifest.decode(
            manifest(",\n  \"stop_qualified\": false"),
        )
        assertTrue(result.isSuccess)
        val record = result.getOrThrow().toCapabilityRecord()
        assertFalse(record.liveReady)
        assertTrue("physical STOP" in record.blockers)
    }

    @Test
    fun wrongSchemaCapabilityOrDigestFailsClosed() {
        assertTrue(SlowMotionCapabilityManifest.decode(manifest().replace("\"schema\": 1", "\"schema\": 2")).isFailure)
        assertTrue(SlowMotionCapabilityManifest.decode(manifest().replace("openpolaris.slow-motion", "other.motion")).isFailure)
        assertTrue(SlowMotionCapabilityManifest.decode(manifest().replace(sha, "not-a-digest")).isFailure)
    }

    @Test
    fun unknownFieldsAndOversizedInputFailClosed() {
        assertTrue(SlowMotionCapabilityManifest.decode(manifest(",\n  \"future_gate\": true")).isFailure)
        val oversized = manifest().replace("slow-qual-0001", "x".repeat(33_000))
        assertTrue(SlowMotionCapabilityManifest.decode(oversized).isFailure)
    }

    @Test
    fun malformedIdentityFailsClosed() {
        assertTrue(SlowMotionCapabilityManifest.decode(manifest().replace("polaris-k3iii-m1", " ")).isFailure)
        assertTrue(SlowMotionCapabilityManifest.decode(manifest().replace("benro-connect-android-1.4.0", "x".repeat(257))).isFailure)
        assertTrue(SlowMotionCapabilityManifest.decode(manifest().replace("fw-build-2026-11-11", "fw\u0000build")).isFailure)
    }

    @Test
    fun decodingDoesNotChangeTheReleaseCapability() {
        assertFalse(SlowMotionCapabilities.current.liveReady)
        assertTrue(SlowMotionCapabilities.current.blockers.isNotEmpty())
    }
}
