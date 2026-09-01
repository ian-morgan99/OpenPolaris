package dev.openpolaris.core.domain

/**
 * App-side configuration for the gimbal app-handshake sequence
 * ([MountSession.authenticate]).
 *
 * Wire-protocol overview (PROTOCOL.md §3, live-captured 2026-09-01):
 *
 *   1. `APP_PASSWORD_INFO` (820) → client asks the gimbal whether a
 *      connection password is required. Response payload: `needed:0;`
 *      or `needed:1;`. If `needed:0`, skip the 821 step.
 *   2. `APP_TOKEN` (821) → client sends its connection token (the
 *      password). Response payload: `token:<echo>;ret:0;` on success
 *      or `ret:1;` on reject. The echo confirms the gimbal accepted
 *      the value.
 *   3. `APP_HELLO` (823) → client identifies itself.
 *      Response payload: `app:<name>;ver:<version>;`. The Benro
 *      app sends `app:benro;ver:<bundleVersion>;`; we use
 *      `app:openpolaris;ver:<this version>;`.
 *
 * The `password` is **never** persisted to disk: it lives only in
 * the in-memory `AuthConfig` of the running [AppViewModel]. The
 * `SessionMarker` records that a password was set
 * ([SessionMarker.passwordSet]) so the UI can prompt for it on
 * next launch, but does not contain the value itself. Storing a
 * cleartext password in a file under `~/.openpolaris/` would be a
 * security regression relative to the Benro app, which keeps it in
 * private storage on-device.
 *
 * @property appName Self-identification string written into the 823
 *   `app:<value>;` field. Defaults to "openpolaris". The Benro app
 *   uses "benro".
 * @property appVersion Self-version string written into the 823
 *   `ver:<value>;` field. Defaults to [DEFAULT_APP_VERSION].
 *   The Benro app sends its bundle version.
 * @property password Connection password (a.k.a. token). `null` means
 *   "no password configured"; the 821 step is then skipped (the
 *   820 probe still runs, so the app is polite even when the
 *   gimbal reports `needed:0`).
 */
data class AuthConfig(
    val appName: String = "openpolaris",
    val appVersion: String = DEFAULT_APP_VERSION,
    val password: String? = null,
) {
    companion object {
        /**
         * App version string sent in the 823 hello payload. Bumped
         * manually when OpenPolaris gains a feature that the gimbal
         * might inspect (the decompile suggests the gimbal doesn't
         * actually branch on this — but we keep a real value so logs
         * are unambiguous).
         */
        const val DEFAULT_APP_VERSION: String = "0.1.0"
    }
}
