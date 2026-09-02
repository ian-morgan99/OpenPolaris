package dev.openpolaris.core.domain

/**
 * Platform-supplied [FirmwareDelivery] factory.
 *
 * `commonMain` declares the contract; each platform (`jvmMain`,
 * `androidMain`, etc.) provides the actual implementation that
 * matches its own ssh subprocess capabilities. The
 * [FirmwareUpdateController] uses this seam to wire up
 * [DeliveryMode.SSH_PIPE] without hard-coding a particular
 * ssh library.
 *
 * ### Trust profile
 *
 * The default is [NoOpFirmwareDelivery] — a sentinel that throws
 * if SSH_PIPE delivery is requested. Platforms that have a working
 * ssh pipeline are expected to override this with a real
 * implementation (see `ScpFirmwareDelivery` in the JVM module).
 *
 * ### Wiring
 *
 * The factory is invoked by `AppViewModel.buildFirmwareDelivery()`
 * every time the user kicks off an upload. Implementations are
 * expected to be lightweight (no I/O at construction time).
 */
expect fun platformFirmwareDelivery(
    host: String,
    port: Int = 22,
    user: String = "root",
): FirmwareDelivery
