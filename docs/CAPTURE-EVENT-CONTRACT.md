# Capture event contract

OpenPolaris must not poll code 266 to determine capture state. Live firmware
maps 266 to a white-balance/configuration read. Capture completion is therefore
event-correlated and fail-closed.

## Runtime sequence

1. Reject another shutter while a capture is `Requested`, `Busy`, or
   `OutcomeUnknown`.
2. Gate new 284/517 polls and allow the current bounded poll window to drain.
3. Close this process's 8080 preview transport. If preview was active, request
   camera preview off (291).
4. Send one 264 subtype-4 shutter command.
5. Observe unsolicited frames received after the request began:
   - a post-request, non-idle 264 `state` is lifecycle evidence and makes the
     capture busy (an idle event by itself cannot identify this shutter);
   - a 773 with a non-empty `path` is file evidence (`size` is not emitted by
     every supported firmware and is not required);
   - the lifecycle evidence followed by the file event is required for
     `Completed`.
6. A watchdog without both signals yields `OutcomeUnknown`, never success.
7. Restore polling, and restore camera/8080 preview exactly once if this view
   model had an active preview before capture.

Unrelated 266, 284, 517, malformed 773, and pre-request frames cannot complete
a capture. An unknown outcome requires explicit operator recovery (check the
card/reconnect); OpenPolaris does not automatically retry an ambiguous shutter.

## Deterministic regression test

Run:

```bash
./gradlew :composeApp:jvmTest --tests dev.openpolaris.ui.AppViewModelCapturePhaseTest
```

The test asserts that a connected session emits no 266 requests, validates the
264+773 correlation, rejects unrelated/malformed frames, and proves timeout is
unknown and blocks a second shutter.

Hardware qualification remains separate: exercise five ordinary captures and
the bounded Astro sequence on each supported camera, verify exact file counts
and integrity, and record preview restoration and Broadcom counter deltas.
