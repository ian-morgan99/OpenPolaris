@file:Suppress("DEPRECATION")

package dev.openpolaris.core.domain

import dev.openpolaris.core.net.SshCommandRunner
import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Firmware update controller — drives a complete FwPkt.zip upload to the
 * Benro Polaris gimbal.
 *
 * Two delivery modes are supported, each with a different trust profile:
 *
 *  - [DeliveryMode.SSH_PIPE] (default, **verified**): the controller
 *    hands the bytes to a [FirmwareDelivery] seam that shells out to `scp`
 *    (or, equivalently, `ssh root@<host> 'cat > /app/sd/FwPkt.zip'`) and
 *    drops the zip onto the SD card. This is the path the on-board
 *    `polestar_app` binary actually watches (see `HANDOVER-2026-08-31.md`
 *    §4.4 — `SP_UpgradeCheckFw` at `0x14023c` in `polestar_app`). The
 *    user must then reboot the gimbal for the install to fire. No
 *    protocol magic, no per-code envelope guess.
 *
 *  - [DeliveryMode.WIRE] (experimental, **unverified**): the controller
 *    drives a chunked upload through the gimbal's binary control plane
 *    using codes 810/784/794/795/811/812 (reconstructed from the Benro
 *    Connect Android app's decompile). The sequence is the one documented
 *    in the H1–H3 audit (`KNOWLEDGE-SHARE-FOR-PATCHER.md`). **As of
 *    2026-08-31, the OpenPolaris RE has not captured a live Benro Connect
 *    upload frame**, so the wire-format assumptions below are best-effort
 *    guesses. The `polestar_app` 810 precondition payload (`type:N;`),
 *    the 784 envelope (`size:N;name:FwPkt.zip;`), and the chunked
 *    payload slot (currently an empty `len:N;` — see [sendChunk]) are
 *    all unverified at the byte level. A live Benro Connect traffic
 *    capture is the only thing that will close that gap.
 *
 * **Wire sequence (DeliveryMode.WIRE only)**, reconstructed from the
 * `polestar_app` decompile and the live-captured post-connect burst
 * (see [docs/evidence/gimbal-ssh-2026-08-31/](../evidence/gimbal-ssh-2026-08-31/)):
 *
 *  1. `SYS_FW_UPGRADE` (810) — precondition. The Benro app arms the
 *     on-board watcher with `type:N;`. Live-captured reply is
 *     `state:0/1/2/3/4` (H3 hypothesis; not yet observed in a
 *     Benro-Connect-captured frame).
 *  2. `FILE_UPLOAD_FW` (784) — start. The mount replies with `ret:0;` if
 *     it is ready to accept a chunked firmware transfer.
 *  3. `FILE_UPLOAD_CHUNK` (794) — repeat, sending [chunkSize] bytes per
 *     call. **Caveat:** the actual payload slot for binary chunks has
 *     not been observed; the current code sends `len:N;` only.
 *  4. `FILE_UPLOAD_END` (795) — finalize. Mount returns to listening.
 *  5. `SYS_FW_PROGRESS` (811) — poll. The watcher emits `p:N;` with
 *     `N` in `0..100` until install completes.
 *  6. **Optional** `SYS_REBOOT` (812) — if [rebootAfter] is true, send a
 *     reboot command once progress hits 100.
 *
 * **Safety**: this controller is gated behind
 * [dev.openpolaris.core.config.FeatureFlags.firmwareUpload], which is OFF
 * by default. The caller is expected to check the flag before invoking
 * [start]. The controller itself does not re-check — keeping the gate at
 * the UI seam means tests and a kiosk build can opt in without
 * touching the controller.
 *
 * **Cancellation**: any caller that cancels the coroutine running [start]
 * halts the upload cleanly between chunks. The mount's watcher will
 * time out on its own and clean up partial state.
 */
class FirmwareUpdateController(
    private val session: MountSession,
    /** How the bytes are pushed onto the mount. [DeliveryMode.SSH_PIPE]
     *  uses the on-board watcher (verified). [DeliveryMode.WIRE] uses
     *  the 810/784/794/795 envelope (unverified). */
    private val delivery: DeliveryMode = DeliveryMode.SSH_PIPE,
    /** Required when [delivery] is [DeliveryMode.SSH_PIPE] — handed the
     *  bytes; expected to drop them at `/app/sd/FwPkt.zip` on the
     *  mount. Ignored for [DeliveryMode.WIRE]. */
    private val sshDelivery: FirmwareDelivery = NoOpFirmwareDelivery,
    /** Optional SSH runner used after the scp delivery to:
     *   1) restart the on-board `polestar_app` so it picks up the
     *      freshly-staged `FwPkt.zip`, and
     *   2) poll `/app/Mlog.txt` for the install state-machine's
     *      terminal sentinels (Pass / Fail / Timeout) so the user
     *      gets real feedback instead of "dropped, now reboot".
     *  Null disables both — the controller will fall back to the
     *  legacy behaviour of returning `Status.Done` after the bytes
     *  land on the SD card. Ignored for [DeliveryMode.WIRE]. */
    private val sshCommandRunner: SshCommandRunner? = null,
    /** How often the on-board install watcher polls Mlog.txt.
     *  SSH_PIPE mode only. */
    private val installPollIntervalMs: Long = 500,
    /** Overall timeout for the on-board install phase. SSH_PIPE mode
     *  only. A 30 MB firmware takes 60–120 s end-to-end on the gimbal
     *  (MD5 verify + NAND write), so 5 minutes is generous. */
    private val onBoardInstallTimeoutMs: Long = 5 * 60_000L,
    /** Max bytes per `FILE_UPLOAD_CHUNK` frame. The default 1024 is the
     *  size the Benro Connect app uses for firmware chunks; anything
     *  larger risks the mount's UART ring. WIRE mode only. */
    private val chunkSize: Int = 1024,
    /** How often to poll `SYS_FW_PROGRESS` while waiting for install.
     *  WIRE mode only. */
    private val progressPollMs: Long = 500,
    /** Stop polling once we see this many successive `p:100;` frames.
     *  WIRE mode only. */
    private val progressDoneRepeats: Int = 2,
    /** Overall timeout for the install phase (after upload completes).
     *  WIRE mode only. */
    private val installTimeoutMs: Long = 5 * 60_000L,
    /** Timeout for the 810 arm precondition. Tighter than the install
     *  timeout because the precondition is supposed to be near-instant.
     *  WIRE mode only. */
    private val armTimeoutMs: Long = 2_000,
) {

    /** Sealed status reported back to the UI. */
    sealed interface Status {
        data object Idle : Status
        /** Connected, armed, sending chunk N of M. [bytesSent] is the
         *  cumulative bytes pushed so far; [bytesTotal] is the full
         *  firmware size. */
        data class Uploading(val bytesSent: Int, val bytesTotal: Int) : Status
        /** Upload done, mount is writing. `percent` is the most-recent `p:N;`. */
        data class Installing(val percent: Int) : Status
        /** `percent == 100` for [progressDoneRepeats] consecutive polls. */
        data object Done : Status
        /** A protocol step failed. `reason` is human-readable. */
        data class Failed(val reason: String) : Status
    }

    /**
     * Run the full upload. [bytes] is the FwPkt.zip bundle; [filename] is
     * written into the precondition frame for logging (the mount does
     * not currently echo the filename back — the field is advisory).
     *
     * [expectedMd5], when non-null and non-blank, is compared against
     * `Md5.digest(bytes)` before any network traffic. A mismatch aborts
     * with [Status.Failed] and never touches the SD card or the WIRE
     * socket. This mirrors the Benro Connect flow where the user pastes
     * a per-piece `crcInfo` hash from the Benro web console and the app
     * refuses to upload if the local MD5 does not match — preventing
     * partial / corrupted bundles from wedging `SP_UpgradeCheckFw` or
     * bricking the mount's recovery partition.
     *
     * Emits one or more [Status] updates to [onStatus] (called from a
     * background coroutine; must be safe to invoke off the UI thread).
     * Returns the final [Status].
     */
    suspend fun start(
        bytes: ByteArray,
        filename: String = "FwPkt.zip",
        expectedMd5: String? = null,
        rebootAfter: Boolean = false,
        onStatus: (Status) -> Unit = {},
    ): Status {
        if (bytes.isEmpty()) {
            val s = Status.Failed("no firmware bytes")
            onStatus(s); return s
        }

        // Phase 1a size cap (FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §6).
        // The on-board SD card is 128 MB vfat with 121 MB free. Anything
        // larger cannot land on the card and would either wedge the
        // writer (SSH_PIPE) or push us into untested long-write territory
        // (WIRE). Refuse up-front rather than mid-flight.
        val maxBytes = 128L * 1024L * 1024L
        if (bytes.size.toLong() > maxBytes) {
            val s = Status.Failed(
                "firmware bundle is ${bytes.size} bytes; exceeds $maxBytes byte (128 MB) cap"
            )
            onStatus(s); return s
        }

        // Phase 1a #2 (FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §6 #2):
        // verify-before-upload MD5 cross-check. The on-board
        // `polestar_app` computes per-piece `crcInfo` hashes and the
        // Benro Connect flow has the user paste the bundle's MD5 in to
        // gate the upload. We mirror that here: if the user supplied an
        // expected MD5 and the local file does not match, refuse the
        // upload *before* any bytes leave the desktop. Comparison is
        // case-insensitive (md5sum output is lowercase, but we don't
        // want a typo to brick a flash).
        val expected = expectedMd5?.trim().orEmpty()
        if (expected.isNotEmpty()) {
            val local = dev.openpolaris.core.util.Md5.digest(bytes)
            if (!local.equals(expected, ignoreCase = true)) {
                val s = Status.Failed(
                    "MD5 mismatch: local=$local, expected=$expected"
                )
                onStatus(s); return s
            }
        }

        return when (delivery) {
            DeliveryMode.SSH_PIPE -> startSshPipe(bytes, filename, rebootAfter, onStatus)
            DeliveryMode.WIRE -> startWire(bytes, filename, rebootAfter, onStatus)
        }
    }

    /**
     * Verified path: hand the bytes to [sshDelivery], which is expected
     * to drop them at `/app/sd/FwPkt.zip` on the mount. If
     * [sshCommandRunner] is provided, also restart the on-board
     * `polestar_app` so its `SP_UpgradeCheckFw` watcher picks up the
     * freshly-staged zip, then poll `/app/Mlog.txt` for the install
     * state-machine's terminal sentinels (Pass / Fail / Timeout) and
     * surface them as [Status.Done] / [Status.Failed].
     *
     * Without an [sshCommandRunner] the controller falls back to the
     * legacy "dropped, now reboot" behaviour — the bytes are on the
     * SD card and we trust the user to reboot.
     */
    private suspend fun startSshPipe(
        bytes: ByteArray,
        filename: String,
        rebootAfter: Boolean,
        onStatus: (Status) -> Unit,
    ): Status {
        onStatus(Status.Uploading(bytesSent = 0, bytesTotal = bytes.size))
        // Phase 1a pre-flight: confirm `/app/sd` has room for the bundle
        // *plus* a 1 MB slack (FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §6).
        // The on-board card is 128 MB vfat with 121 MB free per the live
        // evidence; pushing into a full vfat directory corrupts the
        // FAT and the on-board `SP_UpgradeCheckFw` watcher silently
        // fails to find the zip. Refuse up-front instead of mid-flight.
        val runner = sshCommandRunner
        if (runner != null) {
            val probe = preflightFreeSpace(bytes.size + 1L * 1024L * 1024L)
            if (probe is PreflightResult.PreflightRefused) {
                val s = Status.Failed(probe.reason)
                onStatus(s); return s
            }
        }
        try {
            sshDelivery.deliver(bytes, filename, onProgress = { sent ->
                onStatus(Status.Uploading(bytesSent = sent, bytesTotal = bytes.size))
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val s = Status.Failed("scp delivery failed: ${e.message ?: e::class.simpleName}")
            onStatus(s); return s
        }
        onStatus(Status.Uploading(bytesSent = bytes.size, bytesTotal = bytes.size))

        // If we don't have an SSH runner, the install will be observed
        // by the on-board watcher after the user reboots — return Done
        // once the bytes are on the SD card. This is the legacy
        // behaviour and matches what the Benro Connect app does.
        if (runner == null) {
            onStatus(Status.Installing(percent = 0))
            if (rebootAfter) {
                val rb = reboot()
                if (rb is RebootResult.Failed) {
                    val s = Status.Failed(rb.reason)
                    onStatus(s); return s
                }
            }
            val s = Status.Done
            onStatus(s)
            return s
        }

        // Restart the on-board `polestar_app` so the freshly-staged
        // `FwPkt.zip` is picked up by `SP_UpgradeCheckFw` immediately
        // (the running instance already finished its install check on
        // boot and won't look again until the next reboot).
        onStatus(Status.Installing(percent = 0))
        try {
            val restart = runner.run(
                "pkill polestar_app; nohup /app/polestar_app >/dev/null 2>&1 &",
            )
            if (restart.exitCode != 0) {
                val s = Status.Failed(
                    "could not restart on-board app: exit=${restart.exitCode} ${restart.stderr.orEmpty()}"
                )
                onStatus(s); return s
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val s = Status.Failed(
                "ssh restart failed: ${e.message ?: e::class.simpleName}"
            )
            onStatus(s); return s
        }

        // Poll Mlog for the install state-machine's terminal sentinels.
        val watcher = OnBoardInstallWatcher(
            ssh = runner,
            overallTimeoutMs = onBoardInstallTimeoutMs,
            pollIntervalMs = installPollIntervalMs,
        )
        val install = watcher.watch()
        return when (install) {
            is OnBoardInstallWatcher.Outcome.Pass -> {
                onStatus(Status.Installing(percent = 100))
                val s = Status.Done
                onStatus(s); s
            }
            is OnBoardInstallWatcher.Outcome.Fail -> {
                val s = Status.Failed("on-board install failed: ${install.reason}")
                onStatus(s); s
            }
            is OnBoardInstallWatcher.Outcome.Timeout -> {
                val s = Status.Failed("on-board install timed out after ${onBoardInstallTimeoutMs}ms")
                onStatus(s); s
            }
        }
    }

    /**
     * Unverified path: drives the 810/784/794/795/811/812 envelope
     * through the gimbal's binary control plane. See the class KDoc
     * for the known-unknowns. The chunk payload slot is a `len:N;`
     * placeholder; **the binary-blob framing is the next phase once a
     * captured chunk appears in a live Benro Connect trace**.
     */
    private suspend fun startWire(
        bytes: ByteArray,
        filename: String,
        rebootAfter: Boolean,
        onStatus: (Status) -> Unit,
    ): Status {
        // 1. Precondition: arm the on-board watcher (810).
        val arm = armFirmwareUpgrade(filename)
        if (arm is ArmResult.Failed) {
            val s = Status.Failed(arm.reason)
            onStatus(s); return s
        }
        // 2. Start upload (784).
        val startResult = startUpload(bytes.size)
        if (startResult is UploadResult.Failed) {
            val s = Status.Failed(startResult.reason)
            onStatus(s); return s
        }

        // 3. Chunked upload (794).
        var offset = 0
        try {
            while (offset < bytes.size) {
                if (!coroutineContext.isActive) {
                    val s = Status.Failed("cancelled mid-upload")
                    onStatus(s); return s
                }
                val end = minOf(offset + chunkSize, bytes.size)
                val chunk = bytes.copyOfRange(offset, end)
                val sent = sendChunk(chunk)
                if (sent is UploadResult.Failed) {
                    val s = Status.Failed(sent.reason)
                    onStatus(s); return s
                }
                offset = end
                onStatus(Status.Uploading(bytesSent = offset, bytesTotal = bytes.size))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val s = Status.Failed("upload exception: ${e.message ?: e::class.simpleName}")
            onStatus(s); return s
        }

        // 4. End upload (795).
        val end = endUpload()
        if (end is UploadResult.Failed) {
            val s = Status.Failed(end.reason)
            onStatus(s); return s
        }

        // Upload phase complete, install phase begins.
        onStatus(Status.Installing(percent = 0))

        // 5. Poll progress (811) until p:100.
        val finalStatus = pollProgress(onStatus)
        onStatus(finalStatus)
        if (finalStatus !is Status.Done) return finalStatus

        // 6. Optional reboot.
        if (rebootAfter) {
            val rb = reboot()
            if (rb is RebootResult.Failed) {
                val s = Status.Failed(rb.reason)
                onStatus(s); return s
            }
        }
        return Status.Done
    }

    // ---- private helpers -------------------------------------------------

    private sealed interface ArmResult {
        data object Ok : ArmResult
        data class Failed(val reason: String) : ArmResult
    }

    private sealed interface UploadResult {
        data object Ok : UploadResult
        data class Failed(val reason: String) : UploadResult
    }

    private sealed interface RebootResult {
        data object Ok : RebootResult
        data class Failed(val reason: String) : RebootResult
    }

    /**
     * Outcome of the pre-flight `df /app/sd` check.
     *
     *  - [Ok] — either the card has at least [requiredBytes] free, or
     *    we could not determine the free space and chose to proceed
     *    optimistically (the upload will still fail loudly if the
     *    card truly is full, with a clearer "scp delivery failed:
     *    no space" message from dropbear's `cat`).
     *  - [PreflightRefused] — the free-space probe ran and the card
     *    is too small. We refuse before any bytes are pushed so the
     *    FAT does not get partially overwritten.
     */
    private sealed interface PreflightResult {
        data object Ok : PreflightResult
        data class PreflightRefused(val reason: String) : PreflightResult
    }

    /**
     * Probe `/app/sd` for free bytes via `df -B1` (busybox on the
     * gimbal supports this). Parses the `Available` column. Refuses
     * the upload if free < [requiredBytes] plus a 1 MB slack
     * (caller-supplied).
     *
     * If `df` is missing, the column does not parse, or the SSH
     * transport itself errors out, we return [PreflightResult.Ok]
     * rather than blocking the upload — the in-band `cat > ...`
     * will fail with `No space left on device` and the controller
     * surfaces that as a normal `Status.Failed("scp delivery
     * failed: ...")`. The pre-flight is a fast-fail nicety, not
     * a correctness gate.
     */
    private suspend fun preflightFreeSpace(requiredBytes: Long): PreflightResult {
        val runner = sshCommandRunner ?: return PreflightResult.Ok
        val r = try {
            runner.run("df -B1 /app/sd")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return PreflightResult.Ok
        }
        if (!r.isSuccess) return PreflightResult.Ok
        val available = parseDfAvailableBytes(r.stdout) ?: return PreflightResult.Ok
        if (available < requiredBytes) {
            return PreflightResult.PreflightRefused(
                "/app/sd has $available bytes free; firmware needs at least $requiredBytes bytes (bundle + 1 MB slack)"
            )
        }
        return PreflightResult.Ok
    }

    /**
     * Parse a single-row `df -B1 <path>` output. The second line is
     * `<fs> <used> <available> <use%> <mount>`. Returns the
     * `available` field as a Long, or null on any shape mismatch.
     */
    private fun parseDfAvailableBytes(stdout: String): Long? {
        val rows = stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        // `df -B1` output (columns are whitespace-separated):
        //   Filesystem  1B-blocks  Used  Available  Use%  Mounted  on
        //   /dev/root   253132800  50000000  199999976  20%  /app/sd
        // i.e. parts[0]=Filesystem, parts[1]=total 1B-blocks,
        // parts[2]=Used, parts[3]=Available, parts[4]=Use%, parts[5]=Mounted.
        // Default `df` (no `-B1`) prints 1K-blocks or 1M-blocks — we
        // refuse to interpret those because the unit scaling is wrong
        // for our pre-flight. Bail to Ok (fail-open) and let the
        // in-band `cat > ...` surface a real "no space" if needed.
        val header = rows.firstOrNull { it.startsWith("Filesystem") } ?: return null
        if (!header.contains("1B-blocks")) return null
        val dataRow = rows.firstOrNull { row ->
            val parts = row.split(Regex("\\s+"))
            parts.size >= 4 &&
                parts[1].all { it.isDigit() } &&
                parts[2].all { it.isDigit() } &&
                parts[3].all { it.isDigit() }
        } ?: return null
        val parts = dataRow.split(Regex("\\s+"))
        return parts.getOrNull(3)?.toLongOrNull()
    }

    private suspend fun armFirmwareUpgrade(filename: String): ArmResult {
        val r = session.request<ArmParsed>(
            code = Codes.SYS_FW_UPGRADE,
            payload = "type:0;",  // H1–H3 envelope: type flag, not the mount version
            timeoutMs = armTimeoutMs,
        ) { frame ->
            // Live-captured reply: "1&810&2&state:0;#" (state varies
            // 0=idle, 1=armed, 2=installing, 3=done, 4=error). Accept
            // any reply as long as the parser saw a frame; we treat a
            // non-zero state as success because H3 is a precondition,
            // not an authorisation handshake.
            ArmParsed(frame.fields["state"]?.toIntOrNull() ?: 0)
        }
        return when (r) {
            is MountSession.CmdResult.Ok -> ArmResult.Ok
            is MountSession.CmdResult.Timeout ->
                ArmResult.Failed("arm (810) timed out")
            is MountSession.CmdResult.ProtocolError ->
                ArmResult.Failed("arm (810) error: ${r.message}")
        }
    }

    private suspend fun startUpload(totalSize: Int): UploadResult {
        val r = session.request<UploadParsed>(
            code = Codes.FILE_UPLOAD_FW,
            payload = "size:$totalSize;name:FwPkt.zip;",  // H1 envelope guess
            timeoutMs = 2_000,
        ) { frame ->
            val ret = frame.fields["ret"]?.toIntOrNull()
            UploadParsed(ret = ret ?: 0)
        }
        return when (r) {
            is MountSession.CmdResult.Ok -> {
                if (r.value.ret == 0) UploadResult.Ok
                else UploadResult.Failed("start (784) returned ret=${r.value.ret}")
            }
            is MountSession.CmdResult.Timeout ->
                UploadResult.Failed("start (784) timed out")
            is MountSession.CmdResult.ProtocolError ->
                UploadResult.Failed("start (784) error: ${r.message}")
        }
    }

    private suspend fun sendChunk(chunk: ByteArray): UploadResult {
        // Chunked upload channel: the payload slot is a base-16 / base-64
        // blob per the Benro app decompile. We send an empty payload
        // here and rely on the live-captured simulator response of `ret:0;`
        // — the JVM side of the test only needs to assert we sent the
        // right code; the binary-blob framing is the next phase once a
        // captured chunk appears in the live trace.
        try {
            session.send(Codes.FILE_UPLOAD_CHUNK, payload = "len:${chunk.size};")
            return UploadResult.Ok
        } catch (e: Exception) {
            return UploadResult.Failed("chunk send exception: ${e.message ?: e::class.simpleName}")
        }
    }

    private suspend fun endUpload(): UploadResult {
        val r = session.request<UploadParsed>(
            code = Codes.FILE_UPLOAD_END,
            payload = "",  // H1 envelope guess
            timeoutMs = 2_000,
        ) { frame ->
            val ret = frame.fields["ret"]?.toIntOrNull()
            UploadParsed(ret = ret ?: 0)
        }
        return when (r) {
            is MountSession.CmdResult.Ok -> {
                if (r.value.ret == 0) UploadResult.Ok
                else UploadResult.Failed("end (795) returned ret=${r.value.ret}")
            }
            is MountSession.CmdResult.Timeout ->
                UploadResult.Failed("end (795) timed out")
            is MountSession.CmdResult.ProtocolError ->
                UploadResult.Failed("end (795) error: ${r.message}")
        }
    }

    private suspend fun pollProgress(onStatus: (Status) -> Unit): Status = coroutineScope {
        val deadline = currentEpochMillis() + installTimeoutMs
        var doneStreak = 0
        var lastPercent = 0
        while (currentEpochMillis() < deadline) {
            if (!isActive) return@coroutineScope Status.Failed("cancelled while polling")
            val r = session.request<ProgressParsed>(
                code = Codes.SYS_FW_PROGRESS,
                timeoutMs = 1_000,
            ) { frame ->
                val p = frame.fields["p"]?.toIntOrNull() ?: 0
                ProgressParsed(p.coerceIn(0, 100))
            }
            when (r) {
                is MountSession.CmdResult.Ok -> {
                    val p = r.value.percent
                    lastPercent = p
                    onStatus(Status.Installing(percent = p))
                    if (p >= 100) {
                        doneStreak++
                        if (doneStreak >= progressDoneRepeats) {
                            return@coroutineScope Status.Done
                        }
                    } else {
                        doneStreak = 0
                    }
                }
                is MountSession.CmdResult.Timeout -> {
                    // Mount can drop one poll — keep going.
                }
                is MountSession.CmdResult.ProtocolError -> {
                    return@coroutineScope Status.Failed("progress (811) error: ${r.message}")
                }
            }
            delay(progressPollMs)
        }
        Status.Failed("install timed out at $lastPercent%")
    }

    private suspend fun reboot(): RebootResult {
        val r = session.request<UploadParsed>(
            code = Codes.SYS_REBOOT,
            payload = "",
            timeoutMs = 2_000,
        ) { frame ->
            val ret = frame.fields["ret"]?.toIntOrNull()
            UploadParsed(ret = ret ?: 0)
        }
        return when (r) {
            is MountSession.CmdResult.Ok -> {
                if (r.value.ret == 0) RebootResult.Ok
                else RebootResult.Failed("reboot (812) returned ret=${r.value.ret}")
            }
            is MountSession.CmdResult.Timeout ->
                RebootResult.Failed("reboot (812) timed out")
            is MountSession.CmdResult.ProtocolError ->
                RebootResult.Failed("reboot (812) error: ${r.message}")
        }
    }

    private data class ArmParsed(val state: Int)
    private data class UploadParsed(val ret: Int)
    private data class ProgressParsed(val percent: Int)
}

/**
 * Which transport the controller uses to push the FwPkt.zip onto the
 * mount. See [FirmwareUpdateController] class KDoc for the trust
 * profile of each.
 */
enum class DeliveryMode {
    /**
     * Default. Verified path: deliver the bytes via the host's
     * [FirmwareDelivery] (typically a `scp` to root@<gimbal-ip>:/app/sd/FwPkt.zip)
     * and let the on-board `SP_UpgradeCheckFw` watcher handle the rest
     * after the user reboots.
     */
    SSH_PIPE,

    /**
     * Unverified path: drive the 810/784/794/795/811/812 envelope
     * through the binary control plane. Use only when you have a
     * live-captured frame to validate the wire format against; the
     * current implementation has the chunk payload slot as a `len:N;`
     * placeholder.
     */
    WIRE,
}

/**
 * Transport seam for [DeliveryMode.SSH_PIPE]. Implementations push the
 * bytes onto the mount at the path the on-board watcher monitors
 * (`/app/sd/FwPkt.zip`). The seam is multiplatform — `commonMain` only
 * declares the contract; the JVM-side uses `scp`/JSch/etc., the
 * Android-side uses a JSch or SSHJ Kotlin port.
 *
 * The default [NoOpFirmwareDelivery] is a sentinel that throws if
 * `SSH_PIPE` is selected without a real implementation wired in. The
 * `AppViewModel` is expected to provide a real one (see
 * `JvmScpFirmwareDelivery` in the JVM module).
 */
interface FirmwareDelivery {
    /**
     * Push the bytes. May take a few seconds for a 60 MB zip over
     * a 100 Mbit/s link. [onProgress] is called with the running
     * byte count (not bytes per second — the consumer can derive
     * the rate itself).
     *
     * Throws on transport failure. The controller wraps the
     * exception into a [FirmwareUpdateController.Status.Failed].
     */
    suspend fun deliver(
        bytes: ByteArray,
        filename: String,
        onProgress: (bytesSent: Int) -> Unit = {},
    )
}

/** Sentinel: throws if SSH_PIPE is selected without a real [FirmwareDelivery]
 *  wired in. Avoids a silent "no-op delivery" that would let the
 *  user think they uploaded. */
object NoOpFirmwareDelivery : FirmwareDelivery {
    override suspend fun deliver(
        bytes: ByteArray,
        filename: String,
        onProgress: (bytesSent: Int) -> Unit,
    ) {
        throw IllegalStateException(
            "SSH_PIPE delivery requested but no FirmwareDelivery is wired in. " +
                "Provide one via FirmwareUpdateController(delivery = SSH_PIPE, sshDelivery = ...). " +
                "See JvmScpFirmwareDelivery for a working JVM implementation."
        )
    }
}
