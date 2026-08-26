# Open Polaris — Critical Evaluation (Phase 0/1 close-out)

Date: end of overnight build session. Status: all three target platforms build; desktop runs; Android APK produced; 19/19 tests green.

## What is verified working

| Artifact | Status |
|---|---|
| `:shared` protocol + domain (JVM, Android, iOS metadata) | ✅ builds |
| `:shared:jvmTest` | ✅ 19 tests, 0 failures |
| `:desktopApp` Compose Desktop | ✅ builds & launches |
| `:androidApp` debug APK (~8.9 MB) | ✅ `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| iOS framework compile | ⚠️ requires macOS/Xcode — not verifiable on this Linux host |

## Divergences from the plan (and why)

1. **`JvmConnection` duplicated per platform instead of expect/actual.**
   ARCHITECTURE.md §"MountSession via expect/actual Connection" assumed an expect/actual pair. In practice the JVM and Android socket code are byte-identical, so we copied the file into both source sets. *Decision:* acceptable short-term; when the iOS `NSStreamConnection` actual lands, refactor to a proper `expect class Connection` so the duplication doesn't spread.
2. **`CommandTable` (ARCHITECTURE §3.2) not yet implemented.** CommandBuilder/ResponseParser hard-code codes. Fine for Phase 1 scope; must be table-driven before camera-settings support grows the surface area.
3. **Astro module absent** — deliberately deferred per plan.

## Environment lessons (no-sudo Linux host)

- System Java was JRE-only → full JDK 21.0.2 unpacked to `~/jdks/jdk-21.0.2`; **every gradle call needs `JAVA_HOME=~/jdks/jdk-21.0.2`**.
- Android SDK bootstrapped to `~/android-sdk`, wired via `local.properties` (`sdk.dir`) — **do not commit local.properties**.
- No curl; wget used for downloads.

## KMP gotchas learned (worth remembering)

- KMP modules use `src/jvmMain` / `src/androidMain`, **not** `src/main`. `androidTarget()` does not see jvmMain sources.
- `androidSourceSetLayoutV2` expects the manifest under `src/androidMain/`.
- Common code purity rules that bit us: no `java.nio.Charsets`, no `String.format("%.4f")`, plain `@Volatile` → use `@kotlin.concurrent.Volatile`. Fixed with common helpers (`Ascii.kt`, `Double.format4()`).
- Applying `com.android.application` in a subproject requires it declared `apply false` in the root build script.
- `android.useAndroidX=true` required once any AndroidX transitive dep appears.
- Desktop WindowSizeClass: use `WindowSizeClass.calculateFromSize(DpSize)` behind `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)`; the Activity-based overload is Android-only.
- Compose Desktop on this host needed an explicit `skiko-awt-runtime-linux-x64` runtimeOnly dep; GL rendering fails headless — run with `SKIKO_RENDER_API=SOFTWARE`.
- `gradle.properties` has a duplicate `org.gradle.jvmargs` line (harmless; last wins) — clean up next touch.

## Known gaps / risks

- **MJPEG live-view unverified against real hardware** — URL format and stream framing inferred from APK decompilation only.
- **Placeholder launcher icon** (solid-color PNGs) — replace before any release.
- **No real-device integration test yet.** Everything is validated against the simulated mount and golden wire frames. First physical session should exercise: connect/handshake, status push 284 cadence, jog at half-speed (inverted payload!), goto 519, tracking 531.
- **iOS**: shared module compiles to framework metadata but no app shell exists; needs a macOS machine.
- Half-speed code 536's inverted payload is covered by goldens but is exactly the kind of quirk real hardware may surprise us on.

## Next phase recommendation

1. Real-device smoke test checklist (above).
2. Refactor to `CommandTable` before adding camera settings commands.
3. iOS app shell (macOS required).
4. Replace icon, dedupe gradle.properties, restore expect/actual Connection.

## Phase 2 review (goto, CommandTable, camera)

- **CommandTable refactor paid off immediately**: camera descriptors slotted in with zero
  changes to session plumbing; the table is now the single registry PROTOCOL.md mirrors.
- **Camera breakthrough**: `bin/polestar_app` inside the firmware appfs is unstripped —
  `strings` recovered exact payload formats (`iso:%d;ret:%d;`, `state:%d;bulb:%d;c:%d;`,
  timelapse step payloads) and the index-based set semantics (SP_SetCameraIsoIndex,
  eSortIso/Shutter/FNum). This replaces guesswork with firmware ground truth.
- **Honest limitation kept visible**: numeric codes 258–311 are inferred (built dynamically
  in firmware; APK unavailable). The CameraPane shows an experimental warning and Codes.kt
  documents the uncertainty. Do not remove the warning until hardware-validated.
- **Simulator extended** to answer camera codes, so demo mode exercises the full request/
  response path end-to-end without hardware.
- Tests: 27 green across 5 suites (4 new camera tests: set payloads, get parsers, tolerant
  capture-state parsing, code-range guard).
