---
name: compose-kmp-pitfalls
description: Compose Multiplatform / Kotlin Multiplatform specific pitfalls — expect/actual test resolution, File.delete() semantics, AlertDialog measurement, OutlinedTextField label rotation. Use when debugging KMP unit-test-vs-runtime divergence, layout collapse in dialogs, or platform-specific I/O returning wrong values.
---

# Compose/KMP pitfalls captured during OpenPolaris development

A collection of non-obvious defects that compiled and even passed unit tests, but only manifested in real Android Compose layouts or in specific KMP test runners.

## 1. KMP `expect/actual` test resolution: `commonTest` ≠ `jvmTest`

In a Kotlin Multiplatform project, tests in `commonTest` resolve `expect` declarations to whichever actual the **test runner's host platform** uses — not necessarily JVM. In OpenPolaris:

- `./gradlew :composeApp:testDebugUnitTest` — runs in an Android test JVM, so `commonTest` resolves to the **Android** actual.
- `./gradlew :shared:jvmTest` — runs in a pure JVM, so `jvmTest` resolves to the **JVM** actual.

If the Android and JVM actuals differ, **only the test that uses the same host as the actual you care about will catch the bug.** Conversely, a bug in the Android actual will be caught by `commonTest` tests run via `:composeApp:testDebugUnitTest`, but NOT by `:shared:jvmTest`.

### Concrete example: `PlatformFile.deleteIfExists()` (v0.1.12)

```kotlin
// shared/src/androidMain/.../PlatformFile.kt
actual fun deleteIfExists(): Boolean = (f.delete() || !f.exists())   // WRONG

// shared/src/jvmMain/.../PlatformFile.kt
actual fun deleteIfExists(): Boolean = Files.deleteIfExists(f.toPath())  // correct
```

`Files.deleteIfExists(path)` returns `false` when the file doesn't exist (correct). The Android implementation's `!f.exists()` clause made it return `true` on missing files. The unit test `forgetMarkerWithoutFileIsANoOp` rejected the buggy behaviour — but only when run via `:composeApp:testDebugUnitTest` (which uses the Android actual). If we'd only ever run `:shared:jvmTest`, the bug would have been invisible.

**Rule of thumb:** when the Android and JVM actuals differ, run BOTH `testDebugUnitTest` and `jvmTest` and verify both pass.

## 2. Android `File.delete()` semantics

`java.io.File.delete()` on Android returns `true` if the file was deleted, `false` if the delete failed OR the file didn't exist. There is no native `deleteIfExists`. Don't compose `f.delete() || !f.exists()` — that always returns `true`, defeating the purpose of an idempotent "ensure it's gone" call.

**Correct Android `deleteIfExists`:**
```kotlin
actual fun deleteIfExists(): Boolean = f.delete()
```

**Even better**, use `java.nio.file.Files.deleteIfExists(path.toPath())` — that's the same call the JVM actual uses, and the multiplatform overlap is cleaner.

## 3. `OutlinedTextField` label rotation in narrow columns

When an `OutlinedTextField` is given a column that is too narrow for the label text, the floating label slot **rotates the text 90° to vertical** rather than truncating or wrapping. The text becomes unreadable.

**Symptoms:**
- Vertical Chinese-looking text in a normally-Latin field.
- Label appears at unexpected width.

**Fixes (in order of preference):**
1. Stack the label and field vertically instead of horizontally.
2. Give the field enough column width for the full label.
3. Use a regular `Text` label *outside* the field if the label must be inline.

**Example (Camera stepper, v0.1.12):**
```kotlin
// WRONG: label and field in same Row, field column too narrow
Row {
    Text("ISO", Modifier.width(80.dp))
    OutlinedTextField(value = iso, ...)  // narrow column → label rotates
}

// RIGHT: label on top, field below
Column {
    Text("ISO")
    OutlinedTextField(value = iso, ..., Modifier.fillMaxWidth())
}
```

## 4. `AlertDialog` `text` slot + `Modifier.weight(1f, fill = true)` = dialog collapse

Inside a Material3 `AlertDialog` `text` slot, if you use `Modifier.weight(1f, fill = true)` on a child of a `Column`, the bounded `text` slot will collapse the column to ~284px and the dialog will not use the available width. The cause is a circular measurement: the bounded `text` slot measures children, the child uses `weight(1f)` to fill it, the slot can't grow because the child wants to fill it.

**Fix:** never put `weight(1f, fill = true)` inside the `text` slot. Either remove the weight or use a regular `Box` with `Modifier.fillMaxWidth()` on the child.

**Example (Settings dialog, v0.1.12):** The inner `verticalScroll` Column had `Modifier.weight(1f, fill = true)` and collapsed the Settings dialog to 284px. Removing the weight restored the dialog to full available width.

**Test guard:** `CalloutDialogNoScrollWrapperTest.featureFlagsPaneOwnsZeroVerticalScrolls` asserts the inner Column has no `weight(1f, fill = true)`.

## 5. Nested `verticalScroll` → "Vertically scrollable component was measured with an infinity maximum height constraints" crash

Stacking two `Modifier.verticalScroll(rememberScrollState())` — one on a parent, one on a child — causes Compose to throw an `IllegalStateException` at layout because the inner scroll's `maxHeight = Constraints.Infinity` is unbounded by the parent scroll's bounded viewport. The fix is structural: **exactly one vertical scroll owner per callout/dialog**. The outer `CalloutDialog` Column in `OpenPolarisApp.kt` is the single owner; per-pane `verticalScroll` is removed.

**Test guard:** `CalloutDialogNoScrollWrapperTest` and `MobileResponsiveLayoutContractTest` both assert the one-scroll-owner rule.

## 6. APK byte size is not a build-identity signal

Two distinct builds with different source code can produce APKs of identical size. DEX compression, resource alignment, and signing-block padding all conspire to make byte equality a coincidence-prone check. **Don't dismiss a user report of "this APK is the same as the old one"** on byte-size evidence alone — verify the version label inside the running app (Settings → scroll to footer) and the `versionName` in the installed manifest (`adb shell dumpsys package dev.openpolaris.app | grep versionName`).
