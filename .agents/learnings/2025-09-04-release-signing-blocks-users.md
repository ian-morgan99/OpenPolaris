# Learning: Per-machine debug keystore blocks users from installing a release APK

**Date:** 2025-09-04
**Discovered while:** diagnosing user reports of "app not installed because the package is not valid" and "the APK is the same size as before, so it's not different".
**Closed in:** v0.1.16

## What broke

Before v0.1.16, `androidApp/build.gradle.kts` had **no `signingConfigs` block**, so the release variant inherited the default — Android's per-machine debug keystore (`~/.android/debug.keystore`). Every developer's laptop generates a different debug keystore, so every release APK had a different signing identity.

When a user installed a build from developer A and then tried to update with a build from developer B, Android refused with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ("package is not valid"). The same failure also happened when the user installed a release APK and then tried to install a debug build from the same developer, because debug builds from a different run of `./gradlew` could in principle differ.

The user reported this as: "the APK is the same size" — which is technically true (compression dominates), but the signature is different. Byte-size equality is a **coincidence-prone** build-identity check.

## The fix

A single, deterministic, **committed** release keystore at `androidApp/keystore/release.keystore`. The keystore binary is the trust anchor; the password lives in `androidApp/key.properties` (gitignored). Every release APK is signed with the same key, so users can update in place from v0.1.16 onward.

**Trade-off:** committing a keystore is normally a bad idea for closed-source commercial apps, but for a single-maintainer open-source project, reproducible signature identity is more important than hiding the trust anchor. Anyone who clones the repo can sign their own build, but that build is signed with a *different* identity from the official one, so the install conflict only ever protects the maintainer from a malicious impersonator, not the other way around.

## Critical install note for v0.1.16

The v0.1.16 signature is **different** from any prior build (because the keystore is new). Android will refuse to update in place — the user must **uninstall the existing build first** before installing v0.1.16. This is documented prominently in:

- `CHANGELOG.md` v0.1.16 "Install note — please read before updating"
- The release notes of every GitHub Release tagged `v*.*.*`

If you ever rotate the release keystore again in the future, repeat this warning.

## How to verify a release is correctly signed

```bash
"$ANDROID_HOME/build-tools/<version>/apksigner" verify --verbose --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk
```

Look for `Verifies`, `Verified using v2 scheme (APK Signature Scheme v2): true`, and a `Signer #1 certificate SHA-256 digest` that matches the value in the CHANGELOG entry for that release.

## Generalised rule

For any Android app that ships to external users:

- **Always configure a `signingConfigs.release` block** even if you only ever build locally. Without it, the release variant falls through to the debug keystore, which is per-machine.
- For open-source single-maintainer projects, **committing the keystore is a reasonable trade-off** — reproducibility of the signature identity is more valuable than hiding the trust anchor.
- **When the signing identity changes** (key rotation, new keystore, switch from debug to release), the user must uninstall the old build first. Always loud-document this in release notes.

This pattern is captured in `compose-kmp-pitfalls` §6 (already existed: "APK byte size is not a build-identity signal"). The keystore-in-repo decision is captured in the comment block at the top of `androidApp/build.gradle.kts`.
