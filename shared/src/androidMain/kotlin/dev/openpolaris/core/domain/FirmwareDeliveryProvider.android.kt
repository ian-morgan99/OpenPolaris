package dev.openpolaris.core.domain

/**
 * Android-side implementation of [platformFirmwareDelivery].
 *
 * **As of 2026-08-31, this returns [NoOpFirmwareDelivery] by design.**
 * Android does not ship with an `ssh` binary on `PATH`, and the
 * OpenPolaris dependency set does not include JSch/SSHJ yet. Users
 * who want SSH_PIPE delivery from the Android app can:
 *
 *  1. Add `com.jcraft:jsch` (or `org.apache.sshd:sshd-common`) to
 *     the Android dependencies, then swap this actual for a real
 *     `JschSshDelivery`.
 *  2. Or use [DeliveryMode.WIRE] and rely on the binary control
 *     plane envelope (with the documented "unverified" caveat).
 *
 * Returning a clear [NoOpFirmwareDelivery] is preferable to a
 * silent no-op: the user gets an actionable error message instead
 * of thinking they uploaded.
 */
actual fun platformFirmwareDelivery(
    host: String,
    port: Int,
    user: String,
): FirmwareDelivery = NoOpFirmwareDelivery
