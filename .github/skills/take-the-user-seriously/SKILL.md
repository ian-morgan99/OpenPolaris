---
name: take-the-user-seriously
description: Reproduce OpenPolaris install, startup and other runtime defects on the relevant platform and exact reported artifact using runtime evidence.
---

# Reproduce a reported OpenPolaris runtime failure

Record the reported symptom and match the platform, artifact and installation route. Keep reports from different builds and device environments distinct. The v0.1.6 first-launch Android crash appeared only at runtime when the activity registered a lifecycle callback after RESUMED.

1. Get the exact reported release file, using an available GitHub client. Record its SHA-256, version, build provenance and package/application identity. For a side-by-side old install, preserve user data and use a separate test instance.
2. Reproduce in a disposable emulator or designated test device. Match OS version and app configuration where relevant. For desktop startup, use the installed desktop launcher and inspect its actual Exec target and process output. For Android select an explicit device serial. Use the SDK available on the host and a bounded boot wait.
3. Capture only the relevant evidence. Record the exit status or foreground process, fault or crash trace, and screenshot if UI behavior is involved. Record the user report and any environment differences if the symptom does not reproduce.
4. Verify the corrected artifact on the same path and run the affected regression suite. Keep build, installation, first-launch and feature behavior in separate result rows.

With an already-running Android test target, set TEST_SERIAL, RELEASE_APK, APPLICATION_ID and EVIDENCE_DIR before running:

~~~sh
adb -s "$TEST_SERIAL" shell getprop sys.boot_completed
adb -s "$TEST_SERIAL" install -r "$RELEASE_APK"
adb -s "$TEST_SERIAL" shell monkey -p "$APPLICATION_ID" -c android.intent.category.LAUNCHER 1
adb -s "$TEST_SERIAL" logcat -d -b crash >"$EVIDENCE_DIR/android-crash.log"
adb -s "$TEST_SERIAL" exec-out screencap -p >"$EVIDENCE_DIR/android-launch.png"
~~~

Check the boot response and the install command result before launching. Preserve the existing logs. The published Android application ID is normally `dev.openpolaris.app`. Read the actual artifact for the reported package identity.

For an Android candidate built from source, run `./gradlew :androidApp:assembleDebug` from the current source checkout and record that separately from the downloaded original. For a simulator workflow use the [Demo mode procedure](../openpolaris-demo-mode/SKILL.md).
