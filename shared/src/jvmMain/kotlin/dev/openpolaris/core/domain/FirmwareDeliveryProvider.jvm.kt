package dev.openpolaris.core.domain

import dev.openpolaris.core.net.SshCommandRunner
import dev.openpolaris.core.net.SystemSshCommandRunner

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

actual fun platformFirmwareCommandRunner(
    host: String,
    port: Int,
    user: String,
): SshCommandRunner? = SystemSshCommandRunner(host = host, port = port, user = user)
