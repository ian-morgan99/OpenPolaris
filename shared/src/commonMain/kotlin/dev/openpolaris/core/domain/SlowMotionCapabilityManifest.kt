package dev.openpolaris.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Versioned evidence metadata for a firmware-bound SLOW capability record.
 *
 * This is deliberately a parser and validation boundary, not a runtime
 * permission source. A manifest may be decoded by a qualification tool or a
 * deterministic test harness, but the release default remains the immutable
 * unqualified record in [SlowMotionCapabilities].
 */
@Serializable
data class SlowMotionCapabilityManifest(
    val schema: Int,
    val capability: String,
    @SerialName("firmware_identity") val firmwareIdentity: String,
    @SerialName("gimbal_identity") val gimbalIdentity: String,
    @SerialName("source_app_identity") val sourceAppIdentity: String,
    @SerialName("evidence_bundle_id") val evidenceBundleId: String,
    @SerialName("evidence_bundle_sha256") val evidenceBundleSha256: String,
    @SerialName("route_qualified") val routeQualified: Boolean,
    @SerialName("axis_signs_qualified") val axisSignsQualified: Boolean,
    @SerialName("rate_envelope_qualified") val rateEnvelopeQualified: Boolean,
    @SerialName("acknowledgement_qualified") val acknowledgementQualified: Boolean,
    @SerialName("watchdog_qualified") val watchdogQualified: Boolean,
    @SerialName("stop_qualified") val stopQualified: Boolean,
    @SerialName("disconnect_recovery_qualified") val disconnectRecoveryQualified: Boolean,
    @SerialName("limits_qualified") val limitsQualified: Boolean,
) {
    fun toCapabilityRecord(): SlowMotionCapabilityRecord = SlowMotionCapabilityRecord(
        firmwareIdentity = firmwareIdentity,
        gimbalIdentity = gimbalIdentity,
        sourceAppIdentity = sourceAppIdentity,
        routeQualified = routeQualified,
        axisSignsQualified = axisSignsQualified,
        rateEnvelopeQualified = rateEnvelopeQualified,
        acknowledgementQualified = acknowledgementQualified,
        watchdogQualified = watchdogQualified,
        stopQualified = stopQualified,
        disconnectRecoveryQualified = disconnectRecoveryQualified,
        limitsQualified = limitsQualified,
    )

    private fun validate() {
        require(schema == CURRENT_SCHEMA) { "unsupported SLOW capability schema: $schema" }
        require(capability == CAPABILITY_ID) { "unexpected SLOW capability id" }
        validateText("firmware_identity", firmwareIdentity, MAX_ID_LENGTH)
        validateText("gimbal_identity", gimbalIdentity, MAX_ID_LENGTH)
        validateText("source_app_identity", sourceAppIdentity, MAX_ID_LENGTH)
        validateText("evidence_bundle_id", evidenceBundleId, MAX_ID_LENGTH)
        require(SHA256.matches(evidenceBundleSha256)) {
            "evidence_bundle_sha256 must be 64 hexadecimal characters"
        }
    }

    companion object {
        const val CURRENT_SCHEMA: Int = 1
        const val CAPABILITY_ID: String = "openpolaris.slow-motion"
        const val MAX_MANIFEST_BYTES: Int = 32 * 1024

        private const val MAX_ID_LENGTH = 256
        private val SHA256 = Regex("[0-9a-fA-F]{64}")
        private val JSON = Json {
            ignoreUnknownKeys = false
            isLenient = false
            explicitNulls = true
        }

        fun decode(text: String): Result<SlowMotionCapabilityManifest> {
            if (text.encodeToByteArray().size > MAX_MANIFEST_BYTES) {
                return Result.failure(IllegalArgumentException("SLOW capability manifest is too large"))
            }
            return runCatching {
                JSON.decodeFromString<SlowMotionCapabilityManifest>(text).also { it.validate() }
            }
        }

        private fun validateText(name: String, value: String, maxLength: Int) {
            require(value.isNotBlank()) { "$name must not be blank" }
            require(value.length <= maxLength) { "$name is too long" }
            require(value.none { it.isISOControl() }) { "$name contains a control character" }
        }
    }
}
