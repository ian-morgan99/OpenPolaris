package dev.openpolaris.core.domain

/**
 * User-facing battery classification for field operation.
 *
 * Battery data is advisory: it never changes connection or motion admission.
 * Unknown stays explicit because a missing 778/779 read must not look healthy.
 */
enum class BatterySeverity(val label: String) {
    UNKNOWN("unknown"),
    CRITICAL("critical"),
    LOW("low"),
    NORMAL("normal"),
}

class BatteryAwareness private constructor(
    val percent: Int?,
    val charging: Boolean,
) {
    val severity: BatterySeverity
        get() = when {
            percent == null -> BatterySeverity.UNKNOWN
            percent <= CRITICAL_MAX_PERCENT -> BatterySeverity.CRITICAL
            percent <= LOW_MAX_PERCENT -> BatterySeverity.LOW
            else -> BatterySeverity.NORMAL
        }

    val compactLabel: String
        get() = when (severity) {
            BatterySeverity.UNKNOWN -> "Battery —"
            else -> "Battery ${percent}%"
        }

    val contentDescription: String
        get() {
            val charge = when {
                percent == null -> "Battery level unknown"
                else -> "Battery ${percent}%"
            }
            val power = if (charging) " and charging" else ""
            return "$charge${power}; ${severity.label} field status"
        }

    companion object {
        const val CRITICAL_MAX_PERCENT: Int = 10
        const val LOW_MAX_PERCENT: Int = 20

        fun from(percent: Int?, charging: Boolean): BatteryAwareness =
            BatteryAwareness(percent?.takeIf { it in 0..100 }, charging)
    }
}
