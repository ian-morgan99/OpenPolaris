package dev.openpolaris.core.net

/**
 * Test seam for shelling out to host binaries (`ip`, `nmcli`, `bluetoothctl`).
 *
 * Both `WifiBridge` and `BluetoothProbe` must be unit-testable without root or
 * a Wi-Fi / Bluetooth adapter. Every command they execute is routed through a
 * `ProcessRunner`, so a test can supply a fake that returns canned stdout and
 * exit codes.
 */
fun interface ProcessRunner {
    /** `args[0]` is the executable; stdout is captured and returned; non-zero exit throws. */
    fun run(args: List<String>): String
}

class BridgeException(
    val command: String,
    val exitCode: Int,
    stderr: String,
    cause: Throwable? = null,
) : RuntimeException("$command exited $exitCode: $stderr", cause)

/** Default implementation: forks a real [ProcessBuilder] and waits for it. */
object SystemProcessRunner : ProcessRunner {
    override fun run(args: List<String>): String {
        require(args.isNotEmpty()) { "ProcessRunner.run requires at least the executable name" }
        val cmd = args[0]
        val pb = ProcessBuilder(args).redirectErrorStream(false)
        val proc = try {
            pb.start()
        } catch (t: Throwable) {
            throw BridgeException("Failed to start $cmd", -1, t.message ?: "", t)
        }
        val err = proc.errorStream.bufferedReader(Charsets.UTF_8).readText()
        val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val code = proc.waitFor()
        if (code != 0) {
            proc.destroyForcibly()
            throw BridgeException(cmd, code, err.ifBlank { out })
        }
        return out
    }
}
