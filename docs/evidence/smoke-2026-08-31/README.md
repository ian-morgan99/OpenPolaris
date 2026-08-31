# Smoke run — 2026-08-31

Verifies acceptance of issue #30: a fresh clone can run
`./gradlew :tools:cli-probe:smoke` against the stub without untracked files.

## Repro

```bash
./gradlew :tools:stub-server:installDist
tools/stub-server/build/install/stub-server/bin/stub-server --port 9099 &
./gradlew :tools:cli-probe:smoke --console=plain --args='127.0.0.1 9099'
```

The same step is wired into [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml)
("Run cli-probe smoke against the stub (regression for #30)") so a missing
main class or a regression in the smoke task fails CI before merge.

## Result (excerpt)

```
PASS=73  FAIL=0  SKIP=6
All exercised tests PASSED.
BUILD SUCCESSFUL
```

The smoke covers 11 sections across 73 codes:

1. BURST_PRE_CAMERA (CommandTable.BURST_PRE_CAMERA)
2. Camera info burst (CommandTable.BURST_CAMERA_CODES)
3. Push stream (codes 284, 518, 778, 779, 266, 267)
4. File / SD reads (codes 770, 775, 783, 782, 788, 792, 793)
5. System / device (codes 808, 809, 814, 815, 816, 780, 525, 287, 526)
6. Wi-Fi (codes 799, 800, 801, 802, 806, 807)
7. OMS / dither / limits / autoLevel / settling (codes 824, 825, 539, 541, 547, 543, 537)
8. Handshake / app (codes 820, 822, 823, 517, 524)
9. Camera info (codes 286, 311, 258, 260, 262, 264, 268, 270, 272, 274, 276, 278)
10. Camera setters — DESTRUCTIVE=0 by default; set `DESTRUCTIVE=1` to exercise
    codes 259, 261, 263, 265, 269, 271, 273, 275, 277, 279
11. Blocked — codes 812 (SYS_REBOOT), 813 (SYS_SHUTDOWN), 776 (FILE_SD_FORMAT),
    810 (SYS_FW_UPGRADE), 530 (CALIBRATE_START); never exercised

## Notes

- The smoke task's `mainClass` is `dev.openpolaris.probe.SmokeKt`, declared
  in `tools/cli-probe/build.gradle.kts`. If `Smoke.kt` is removed/moved
  without updating the build script, the CI step fails on the first
  `compileKotlin` invocation.
- The 5 blocked codes and the 10 destructive camera setters are deliberately
  skipped to keep the smoke safe to run unattended.
