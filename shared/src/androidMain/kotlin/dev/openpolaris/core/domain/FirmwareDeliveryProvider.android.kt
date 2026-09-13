package dev.openpolaris.core.domain

import dev.openpolaris.core.net.SshCommandRunner
import dev.openpolaris.core.net.SshjCommandRunner

/**
 * Android-side implementation of [platformFirmwareDelivery].
 *
 * Wires the requested host/port/user into a [SshjFirmwareDelivery] that pushes
 * the FwPkt.zip onto `/app/sd/FwPkt.zip` over SSHJ (a pure-JVM SSH client), so
 * the APK needs no `ssh`/`scp` binary on PATH. This is the same "verified path"
 * the JVM-side [ScpFirmwareDelivery] uses; only the transport differs.
 *
 * The gimbal's dropbear sshd allows **root with an empty password** by default,
 * so no key setup is required for a stock gimbal. See [SshjFirmwareDelivery].
 */
actual fun platformFirmwareDelivery(
    host: String,
    port: Int,
    user: String,
): FirmwareDelivery = SshjFirmwareDelivery(host = host, port = port, user = user)

/**
 * Android-side implementation of [platformFirmwareCommandRunner].
 *
 * Returns a [SshjCommandRunner] so the [FirmwareUpdateController] can run the
 * post-delivery probes (the `/app/sd` free-space pre-flight, the extraction
 * readiness check, and the mandatory `sync; /sbin/reboot`) over SSHJ without an
 * `ssh` binary on PATH. See [SshjCommandRunner].
 */
actual fun platformFirmwareCommandRunner(
    host: String,
    port: Int,
    user: String,
): SshCommandRunner? = SshjCommandRunner(host = host, port = port, user = user)
