package dev.openpolaris.core.net

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

/**
 * Android-side factory for the SSHJ [SSHClient] used by the firmware delivery
 * seam ([dev.openpolaris.core.domain.SshjFirmwareDelivery]) and the
 * post-delivery probe runner ([SshjCommandRunner]).
 *
 * The Benro Polaris gimbal runs **dropbear** sshd with an **empty-password root**
 * account (per the live-captured `sshd_config` in
 * `docs/evidence/gimbal-ssh-2026-08-31/`). SSHJ is a pure-JVM client, so it runs
 * on Android without shelling out to an `ssh` binary — which is what the JVM-side
 * [SystemSshCommandRunner] relies on but Android does not have.
 *
 * ### Host-key handling
 *
 * The gimbal's host key rotates whenever the firmware is reflashed, so we accept
 * any host key ([PromiscuousVerifier]) rather than pinning one. This matches the
 * `StrictHostKeyChecking=no` behaviour the JVM-side [SystemSshCommandRunner] uses,
 * so a fresh app install "just works" against a reflashed gimbal.
 *
 * ### Authentication
 *
 * Password auth with the (default empty) root password. The [password] is held
 * only for the duration of a single connection and is never persisted.
 */
internal object SshjClientFactory {

    /**
     * Build, connect, and authenticate an [SSHClient] for the gimbal. The caller
     * is responsible for [SSHClient.close].
     *
     * @throws java.io.IOException if the host is unreachable or auth fails.
     */
    fun newConnectedClient(
        host: String,
        port: Int,
        user: String,
        password: String,
    ): SSHClient {
        val client = SSHClient()
        // The gimbal's host key rotates on every firmware reflash, so accept any
        // host key rather than pinning one (mirrors the JVM-side
        // StrictHostKeyChecking=no behaviour).
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host, port)
        // dropbear root with an empty password by default; [password] is
        // configurable for hardened gimbals.
        client.authPassword(user, password)
        return client
    }
}
