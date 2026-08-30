package dev.openpolaris.core.astro

import kotlin.math.PI

/** Internal angle helpers shared across the ephemeris layer. */
internal fun Double.toRad(): Double = this * (PI / 180.0)

/** Internal angle helpers shared across the ephemeris layer. */
internal fun Double.toDeg(): Double = this * (180.0 / PI)

/** Wrap an angle in degrees into the range [0, 360). */
internal fun Double.normalizeDeg(): Double = ((this % 360.0) + 360.0).mod(360.0)
