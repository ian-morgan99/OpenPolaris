package dev.openpolaris.core.domain

/**
 * JVM-side implementation of [platformFirmwareDelivery] — wires the
 * requested host/port/user into a [ScpFirmwareDelivery] that shells
 * out to the platform `ssh` binary.
 */
actual fun platformFirmwareDelivery(
    host: String,
    port: Int,
    user: String,
): FirmwareDelivery = ScpFirmwareDelivery(host = host, port = port, user = user)
