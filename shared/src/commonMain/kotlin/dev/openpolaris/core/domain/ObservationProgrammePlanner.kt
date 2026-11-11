package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Pure observation-programme plans shared by the mobile and desktop shells.
 *
 * This is deliberately a plan, not a transport controller.  A caller must
 * still acquire the camera/motion owners, execute each step, and wait for the
 * existing correlated capture evidence before advancing.  Keeping the
 * programme description separate from wire commands lets the UI expose the
 * Astro WebUI surface without guessing undocumented firmware semantics.
 */
object ObservationProgrammePlanner {
    @Serializable
    enum class Kind {
        LAPSE,
        HDR,
        FOCUS_STACK,
        PANORAMA,
    }

    sealed interface Request {
        val intervalMs: Long
        val preDelayMs: Long

        data class Lapse(
            val shotCount: Int,
            override val intervalMs: Long,
            override val preDelayMs: Long = 0L,
            val exposureIndex: Int? = null,
        ) : Request

        data class Hdr(
            val baseExposureIndex: Int,
            val bracketOffsets: List<Int>,
            override val intervalMs: Long,
            override val preDelayMs: Long = 0L,
        ) : Request

        data class FocusStack(
            val startPosition: Double,
            val endPosition: Double,
            val stepCount: Int,
            override val intervalMs: Long,
            override val preDelayMs: Long = 0L,
            val exposureIndex: Int? = null,
        ) : Request

        data class Panorama(
            val rows: Int,
            val columns: Int,
            val yawStepDeg: Double,
            val pitchStepDeg: Double,
            override val intervalMs: Long,
            override val preDelayMs: Long = 0L,
            val exposureIndex: Int? = null,
        ) : Request
    }

    @Serializable
    data class Step(
        val ordinal: Int,
        val label: String,
        val delayBeforeMs: Long,
        val exposureIndex: Int? = null,
        val focusPosition: Double? = null,
        val yawOffsetDeg: Double? = null,
        val pitchOffsetDeg: Double? = null,
    )

    @Serializable
    data class Plan(
        val kind: Kind,
        val steps: List<Step>,
    ) {
        init {
            require(steps.isNotEmpty()) { "programme must contain at least one step" }
            require(steps.map { it.ordinal } == (1..steps.size).toList()) {
                "programme ordinals must be contiguous and one-based"
            }
        }

        val captureCount: Int get() = steps.size
        val scheduledDelayMs: Long get() = steps.sumOf { it.delayBeforeMs }
    }

    fun plan(request: Request): Plan {
        require(request.intervalMs >= 0L) { "intervalMs must be >= 0" }
        require(request.preDelayMs >= 0L) { "preDelayMs must be >= 0" }
        return when (request) {
            is Request.Lapse -> lapse(request)
            is Request.Hdr -> hdr(request)
            is Request.FocusStack -> focusStack(request)
            is Request.Panorama -> panorama(request)
        }
    }

    private fun lapse(request: Request.Lapse): Plan {
        require(request.shotCount in 1..10_000) { "shotCount must be between 1 and 10000" }
        return Plan(
            Kind.LAPSE,
            (1..request.shotCount).map { ordinal ->
                Step(
                    ordinal = ordinal,
                    label = "Frame $ordinal/${request.shotCount}",
                    delayBeforeMs = if (ordinal == 1) request.preDelayMs else request.intervalMs,
                    exposureIndex = request.exposureIndex,
                )
            },
        )
    }

    private fun hdr(request: Request.Hdr): Plan {
        require(request.bracketOffsets.isNotEmpty()) { "HDR requires at least one bracket" }
        require(request.bracketOffsets.size <= MAX_HDR_BRACKETS) { "HDR bracket is too large" }
        require(request.bracketOffsets.distinct().size == request.bracketOffsets.size) {
            "HDR bracket offsets must be distinct"
        }
        require(0 in request.bracketOffsets) { "HDR bracket must contain the base exposure" }
        require(request.baseExposureIndex in 0..100) { "base exposure index must be 0..100" }
        val offsets = request.bracketOffsets.sorted()
        return Plan(
            Kind.HDR,
            offsets.mapIndexed { index, offset ->
                Step(
                    ordinal = index + 1,
                    label = "HDR ${index + 1}/${offsets.size}",
                    delayBeforeMs = if (index == 0) request.preDelayMs else request.intervalMs,
                    exposureIndex = (request.baseExposureIndex + offset).also {
                        require(it in 0..100) { "HDR exposure index must remain 0..100" }
                    },
                )
            },
        )
    }

    private fun focusStack(request: Request.FocusStack): Plan {
        require(request.stepCount in 1..500) { "focus stack stepCount must be between 1 and 500" }
        require(request.startPosition.isFinite() && request.endPosition.isFinite()) {
            "focus positions must be finite"
        }
        require(abs(request.endPosition - request.startPosition) <= 100.0) {
            "focus stack travel must be within the qualified planner envelope"
        }
        return Plan(
            Kind.FOCUS_STACK,
            (0 until request.stepCount).map { index ->
                val fraction = if (request.stepCount == 1) 0.0 else index.toDouble() / (request.stepCount - 1)
                Step(
                    ordinal = index + 1,
                    label = "Focus ${index + 1}/${request.stepCount}",
                    delayBeforeMs = if (index == 0) request.preDelayMs else request.intervalMs,
                    exposureIndex = request.exposureIndex,
                    focusPosition = request.startPosition + (request.endPosition - request.startPosition) * fraction,
                )
            },
        )
    }

    private fun panorama(request: Request.Panorama): Plan {
        require(request.rows in 1..100) { "panorama rows must be between 1 and 100" }
        require(request.columns in 1..100) { "panorama columns must be between 1 and 100" }
        require(request.yawStepDeg.isFinite() && request.pitchStepDeg.isFinite()) {
            "panorama steps must be finite"
        }
        require(abs(request.yawStepDeg) <= 180.0 && abs(request.pitchStepDeg) <= 90.0) {
            "panorama steps exceed the planner envelope"
        }
        val steps = buildList {
            var ordinal = 1
            for (row in 0 until request.rows) {
                val reverse = row % 2 == 1
                for (columnIndex in 0 until request.columns) {
                    val column = if (reverse) request.columns - 1 - columnIndex else columnIndex
                    add(
                        Step(
                            ordinal = ordinal++,
                            label = "Panorama ${row + 1}/${request.rows}, ${column + 1}/${request.columns}",
                            delayBeforeMs = if (size == 0) request.preDelayMs else request.intervalMs,
                            exposureIndex = request.exposureIndex,
                            yawOffsetDeg = column * request.yawStepDeg,
                            pitchOffsetDeg = row * request.pitchStepDeg,
                        ),
                    )
                }
            }
        }
        return Plan(Kind.PANORAMA, steps)
    }

    private const val MAX_HDR_BRACKETS = 900
}
