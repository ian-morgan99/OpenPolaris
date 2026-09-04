# Learning: Picker result is dropped silently on rotation

**Date:** 2025-09-04
**Discovered while:** closing issue #49 — firmware picker ignored the file the user picked after a device rotation, and had no cancel feedback.
**Closed in:** v0.1.16

## What broke

`MainActivity` registered an `ActivityResultLauncher` and stored the result-callback on a `companion object` field (`FilePickerRegistry.pendingCallback`). `onDestroy` cleared that field. On rotation:

1. `MainActivity.onDestroy` runs → `pendingCallback = null`.
2. AndroidX re-delivers the activity result to the new activity's launcher.
3. The launcher fires, but its callback is null → the URI is dropped, no error, no status update.
4. The UI is stuck in "picker open" state with no indication anything went wrong.

Separately, when the user **cancelled** the picker, the cancel handler in `AppViewModel.pickFirmwareFile` did `if (path == null) return@pickFile` silently — no status message, no log, no UI change.

## The fix (process-scoped bridge in `commonMain`)

A `PickerBridge` `object` in `shared/.../commonMain` holds the last pick result. Crucially:

- It lives in `commonMain` (not `androidMain`) so it has **no Android types** and can be unit-tested from `commonTest`.
- It is **process-scoped**, so it survives rotation without any lifecycle binding.
- `MainActivity.onCreate` (via `LaunchedEffect(viewModel)`) drains the result into the `ViewModel` once the new composition is ready.

## The test suite

[PickerBridgeTest.kt](/home/ian/Documents/VSCodeProjects/OpenPolaris/shared/src/commonTest/kotlin/dev/openpolaris/core/io/PickerBridgeTest.kt) — 13 tests pinning the begin/in-flight/result/consume state machine. These tests can run on any KMP target host (JVM, Android, JS, …) because the bridge is pure Kotlin.

## Generalised rule

Any time a `MainActivity`-lifecycle-bound resource is used to bridge into a `ViewModel` (file picker result, permission result, share intent, …), ask: **what happens between `onDestroy` and the new `onCreate`?** If the answer is "the resource is recreated empty", you have a rotation-drop bug. Buffer in a process-scoped `object` in `commonMain`; drain from `onCreate`.

This pattern is captured in `compose-kmp-pitfalls` §7.
