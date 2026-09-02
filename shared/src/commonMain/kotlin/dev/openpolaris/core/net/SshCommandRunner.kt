package dev.openpolaris.core.net

/**
 * Test seam for executing a single command on the gimbal over SSH.
 *
 * Mirrors the role of [ProcessRunner] (which shells out to host
 * binaries like `ip`, `nmcli`, `bluetoothctl`) but targets the
 * remote side. Both `ScpFirmwareDelivery` and
 * `OnBoardInstallWatcher` route through this seam so the full
 * install pipeline can be unit-tested without a live gimbal.
 *
 * ### Why a new interface
 *
 * The SSH-byte-stream protocol used by `ScpFirmwareDelivery` is
 * one-shot and forward-only (stdin → remote file, stdout empty).
 * The on-board install state machine, by contrast, requires
 * reading from a remote log file (`/app/Mlog.txt`) repeatedly
 * until a sentinel line appears. That is a different shape: we
 * need a *captured* stdout and an *exit code*, not a streaming
 * sink. Keeping the two seams separate keeps each one focused.
 *
 * ### Implementation notes
 *
 * Implementations should respect the same host-key/identity
 * options as `ScpFirmwareDelivery` (the gimbal's host key may
 * rotate on firmware reflash, so `StrictHostKeyChecking=no` is
 * the right default for first-flash workflows).
 */
fun interface SshCommandRunner {
    /**
     * Run [command] on the gimbal. Returns the captured stdout
     * and exit code.
     *
     * Implementations must NOT throw on a non-zero exit code
     * unless the SSH *transport* itself failed (e.g. the host
     * rejected our auth, the connection dropped, the binary
     * was not found). A non-zero exit from the remote command
     * is a normal outcome that the caller decides how to
     * interpret — for example, `test -f /app/sd/FwPkt.zip`
     * returning 1 is a perfectly valid signal.
     *
     * Implementations should be cancellable via the calling
     * coroutine's cancellation: cancelling the call must
     * destroy any subprocess the runner forked.
     */
    fun run(command: String): SshCommandResult
}

/**
 * Outcome of a single SSH command. [exitCode] is the remote
 * shell's exit status. [stdout] is the captured UTF-8 output
 * (truncated to whatever the runner's buffer allows). [stderr]
 * is captured separately so the caller can surface it on
 * failure paths; for benign polls (e.g. `cat /app/Mlog.txt`)
 * the caller will usually ignore both.
 */
data class SshCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = "",
) {
    /** True when the remote command succeeded. */
    val isSuccess: Boolean get() = exitCode == 0
}
