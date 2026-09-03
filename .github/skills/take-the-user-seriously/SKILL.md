---
name: take-the-user-seriously
description: When a user reports a runtime defect (crash, install failure, "won't open", "not valid", "broken"), reproduce it on an emulator/device and capture empirical runtime evidence before disputing. Never dismiss user reports based on static binary inspection alone.
---

# Take the user seriously — reproduce before disputing

## The lesson

When a user reports that an APK "won't open", "is not valid", "crashes on install", or similar runtime defect, **the first action is to reproduce the symptom on an emulator and capture a logcat/screenshot** — *not* to inspect the published binary and argue that the symptom must belong to a different build.

Static binary inspection (package names, dex byte counts, file sizes, manifest grep) confirms the *artefact* is well-formed. It does **not** exercise the *runtime*. Many classes of defect — `IllegalStateException` from lifecycle violations, `Resources$NotFoundException` from theme/locale mismatches, `NoClassDefFoundError` from ProGuard, native crashes, `IllegalAccessError` from R8 — only surface at first launch on a real (or emulated) device. They cannot be ruled out by reading the binary.

The cost of getting this wrong is high: it costs the user's trust, it leaves a real bug unfixed across multiple release cycles, and it forces the user to spend extra rounds proving what they already know.

## What went wrong in OpenPolaris v0.1.6 → v0.1.7

User reported the published v0.1.6 APK crashes on install. The agent:
1. Looked at the published APK in the GitHub release asset.
2. Noted the package name was `dev.openpolaris.com` (a stale local build) rather than the v0.1.6 applicationId `dev.openpolaris.app`.
3. Concluded the crash must be "not our app" and closed the issue.
4. Was pushed back. Re-inspected bytes. Re-asserted the same conclusion.
5. Only when the user demanded "show me screenshots of it working" did the agent stand up an Android 14 emulator, install the actual v0.1.6 APK, and capture a real `FATAL EXCEPTION` from logcat:
   ```
   java.lang.IllegalStateException: LifecycleOwner ... is attempting to register while current state is RESUMED
   ```
   …which **was** the v0.1.6 APK, **was** our app, and **did** crash at first launch.

The user was right from the first report. The agent was wrong from the first response.

## The procedure — when a user reports a runtime defect

1. **Believe the report.** Treat the user's symptom (crash / won't open / not valid) as a fact about the runtime, even if you suspect the user may have a different build installed.
2. **Reproduce on a real device or emulator first.** Stand up an emulator (`/home/ian/android-sdk/emulator/emulator -avd polaris` on this box), wait for `sys.boot_completed=1`, install the **exact published artefact** (`gh release download <tag>`), launch via `monkey -p <pkg> -c android.intent.category.LAUNCHER 1`, capture `logcat -d` and `screencap`.
3. **Match the user's evidence.** If they posted a screenshot, the package name in their screenshot is the one to reproduce against — including if it looks like a stale build. (Stale local builds are a real support signal, not a dismissable one.)
4. **If reproduction succeeds, you have the bug.** Fix it; don't argue with the user about whose fault it is.
5. **If reproduction fails, the user's environment may be different.** Now is the time to ask them for their device's OS version, logcat from their device, etc. — but only *after* you've established your reproduction.

## The "trust ladder" the agent must respect

A user reporting a defect is, by default, **telling the truth about their experience**. To override that:
- You must produce positive evidence the user is mistaken (reproduction on the same release asset on a known-good environment, plus diff against the user's reported build).
- The bar is "here is my reproduction, with logcat/screenshot, that contradicts the report" — not "I read the binary and it looks fine."

A user who repeats a report after being told it's wrong is **doubling down on truth**, not being stubborn. The right response is to take the second report even more seriously, not to re-assert the first conclusion.

## What this does NOT mean

- Don't blindly accept every claim. The user can be wrong about *why* something is broken. They are almost never wrong about *whether* it is broken from their perspective.
- Don't skip reproduction even if the user's report seems plausible. Reproduction is what converts a plausible report into a diagnosed bug.
- Don't ship a fix without reproduction. "It builds" is not "it works." A fix that builds but isn't reproduced is a guess that has wasted a release.

## Quick reproduction recipe for this project (Android Compose Multiplatform)

```bash
# Boot emulator
/home/ian/android-sdk/emulator/emulator -avd polaris -no-window -accel auto -gpu swiftshader_indirect &
/home/ian/android-sdk/platform-tools/adb wait-for-device
# wait for boot
until [ "$(/home/ian/android-sdk/platform-tools/adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done

# Get the *exact published* APK — never build a fresh one for repro
gh release download v0.1.6 --pattern '*.apk' --dir /tmp/repro
# Or build debug to verify a fix:
./gradlew :androidApp:assembleDebug

# Install and launch
adb install -r /tmp/repro/openpolaris-v0.1.6.apk
adb logcat -c
adb shell monkey -p dev.openpolaris.app -c android.intent.category.LAUNCHER 1
sleep 5
adb shell ps -A | grep polaris           # process alive?
adb shell dumpsys activity activities | grep ResumedActivity   # top activity?
adb logcat -d | grep -E 'FATAL|AndroidRuntime.*polaris'        # any crash?
adb exec-out screencap -p > /tmp/repro.png                     # visual proof
```

## Related repo conventions

- The user has, in the past, installed stale local builds (`dev.openpolaris.com` package) alongside the published release (`dev.openpolaris.app` package). When the user reports a crash, install BOTH the stale and the published APKs in separate emulator sessions to determine which one matches the user's symptom.
- The applicationId is `dev.openpolaris.app`; the Kotlin source package is `dev.openpolaris.android`. The two diverge because Compose Multiplatform splits "namespace" (Kotlin package) from "applicationId" (manifest package). When grepping, search both.
- Locked landscape orientation (`android:screenOrientation="sensorLandscape"`) means the screenshot is always 2340×1080 or similar wide.
