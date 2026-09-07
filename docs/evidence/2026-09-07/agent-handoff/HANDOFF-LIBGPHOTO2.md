# Handoff to the libgphoto2 agent

**Date**: 2026-09-07 14:00
**From**: OpenPolaris feature-test session
**To**: ian-morgan99/libgphoto2 agent working on #48 / #49
**Repo**: https://github.com/ian-morgan99/libgphoto2.git
**Status**: ready to action

## TL;DR

You (the libgphoto2 agent) need to know what is already proven at the
**gimbal/protocol level** so you don't redo that work, and what you own
at the **libgphoto2 source-code level** so you have a clean scope.

The OpenPolaris side ran a live protocol sweep on 2026-09-07 against the
**2026-09-07-k1ii-k3iii-candidate** build (sw:6.0.0.54, FwVer 4.0.0.32)
with a K-3 III connected (lsusb 25fb:0189). Full evidence is at:

- [docs/evidence/2026-09-07/protocol-mapping/PROTOCOL-MAP-2026-09-07.md](https://github.com/ian-morgan99/OpenPolaris/blob/main/docs/evidence/2026-09-07/protocol-mapping/PROTOCOL-MAP-2026-09-07.md)
- [docs/evidence/2026-09-07/protocol-mapping/transcripts/cli-probe-transcript-2026-09-07-1336.txt](https://github.com/ian-morgan99/OpenPolaris/blob/main/docs/evidence/2026-09-07/protocol-mapping/transcripts/cli-probe-transcript-2026-09-07-1336.txt)
- [OpenPolaris#66](https://github.com/ian-morgan99/OpenPolaris/issues/66) — TRACKING issue for the map

## What we already know (you do NOT need to re-prove)

| What | Evidence | Code that works |
|---|---|---|
| The K-3 III is **fully identified** by the firmware | 286 returns `manufacturer:ricoh imaging company, ltd.;model:pentax k-3 mark iii;state:1;storage:2;photoFormat:2;` | 286 (live) |
| The Pentax vendor mode is **enabled** | Mlog lines `camera_init: Pentax vendor mode enabled, function flags 0x00000000` | (push event) |
| Battery 100%, SD 121GB, Wi-Fi 2.4GHz, HDMI off | 778, 775, 802, 300 all return the expected values | 778, 775, 802, 300 (live) |
| Push events (525 IMU temperature) work | `Tempa<hex16>;` pushed every ~30s | 525 (push) |
| The `state:-2` is a **pgphoto runtime** issue, not a protocol issue | 286 says state:1, sp_Gphoto_Init says state:-2 | (runtime layer split) |

So the **protocol layer is healthy** and the **camera firmware can identify and report on the K-3 III**. The break is at the pgphoto runtime layer that mediates between the SP code and the libgphoto2 on-disk libs.

## What is broken and is on the runtime side, not libgphoto2

| Symptom | Where | Reference |
|---|---|---|
| `sp_Gphoto_Init` returns -2 | pgphoto runtime can't find/load iolibs | [patcher#38](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/38) |
| Port 8080 (live view) is not bound | pgphoto never starts (no /app/bin/pgphoto) | [patcher#39](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/39) |
| The deployed `libgphoto2_port.so.12` is a 38,620-byte stripped binary | iolibs lookup path is wrong (looks for `../lib/libgphoto2_port/0.12.0/iolibs/iolibs/`, actual is at `0.12.2/usb1.so`) | [patcher#38 comment](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/38) |
| `restart_gphoto` logs `nohup: can't execute '/app/bin/pgphoto': No such file or directory` | `/app/bin/` is empty (patcher install_stage2.sh didn't populate it) | [patcher#39](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/39) |
| Codes 258-278 (camera info burst) do not reply | Pentax camlib path is blocked by the broken sp_Gphoto_Init | (derived from #38) |
| Code 808 (SYS_VERSION) does not reply | Quirk 1 in [POLARIS-FUNCTIONS-REPORT.md](https://github.com/ian-morgan99/OpenPolaris/blob/main/docs/POLARIS-FUNCTIONS-REPORT.md) | (well-known) |
| Codes 786-799 (file management) — some destructive | Code 789 (FILE_DELETE_ALL) deleted 43.5 MB of JPEGs with a malformed payload, no Mlog trace | [OpenPolaris#64](https://github.com/ian-morgan99/OpenPolaris/issues/64) |

## What you (libgphoto2 agent) own

The two open issues that are yours are **#48 (K-1 II -6)** and **#49
(WB/aperture false-failure)**. Both say:

> Use a clean checkout at an exact SHA and a directly attached K-1 II
> to determine whether the same failure reproduces with this
> repository's own build and the camera directly attached.

**The cleanest reproduction recipe from the OpenPolaris side**:

1. **Build environment** — `/home/ian/Documents/VSCodeProjects/LibGphoto2/libgphoto2` is the local clone, HEAD is `6aa3e4e66240d4b4d68a65b75631e0f6aadf308a` (`ptp2/pentax: fix K-3 III capture and setting verification`). This is the same SHA the patcher is using on the gimbal.
2. **K-3 III specific code paths** — `camlibs/ptp2/pentax-utils.{c,h}` (the refactored capture/reconcile helpers, PR #43), `camlibs/ptp2/library.c` (Pentax vendor mode), `camlibs/ptp2/cameras/pentaxmodern.c` (modern Pentax DSLRs). These are what run when the gimbal's ptp2.so is the K-3 III driver.
3. **K-1 II specific code paths** — the same files, but the d02c code path is gated by `8e9560ca9 ptp2/pentax: gate d02c cross process on k3iii family`. K-1 II takes a different path because the `k3iii family` gate excludes it.
4. **Test infrastructure** — `tests/test-pentax-utils.c` has a `MockTransfer` that simulates PTP responses, including error injection for the short-block (issue #35) and zero-block (issue #34) regressions.
5. **Test commands for the K-1 II** — per #48:
   ```bash
   gphoto2 --debug --debug-logfile=k1ii-capture-clean.log --capture-image
   ```
   Record the **first failing PTP transaction**, not just the final `-6`.
6. **Test commands for WB/aperture** — per #49:
   ```bash
   gphoto2 --set-config /main/status/pentaxdirectwb=daylight
   gphoto2 --get-config /main/status/pentaxdirectwb
   gphoto2 --set-config /main/status/pentaxdirectaperture=f/4
   gphoto2 --get-config /main/status/pentaxdirectaperture
   ```
   The key question is **which exact transaction first produces the error**.

## Where OpenPolaris can help if you need it

If your repo-local reproduction **fails** the same way the Polaris does,
the ownership moves to libgphoto2 — please open a PR against
`origin/master` with the fix, even a draft.

If your repo-local reproduction **passes**, the ownership stays on the
Polaris runtime — please comment on [patcher#38](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/38) with the
PASS result so the patcher team has a confirmed-clean reference build.

## Cross-references

- [OpenPolaris#66](https://github.com/ian-morgan99/OpenPolaris/issues/66) — protocol map tracking
- [OpenPolaris#63](https://github.com/ian-morgan99/OpenPolaris/issues/63) — camera parity inventory (parent)
- [patcher#35](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/35) — K-3 III MTP control path
- [patcher#36](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/36) — K-3 III 0xa008/NoUpdateImage
- [patcher#37](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/37) — K-3 III -1005 before delayed delivery
- [patcher#38](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/38) — iolibs lookup defect
- [patcher#39](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues/39) — /app/bin/ empty
- [nina-pentax-spec#1](https://github.com/ian-morgan99/nina-pentax-spec/issues/1) — K-1 II -6 capture failure

## Tooling we have ready for you

The OpenPolaris workspace has the cli-probe binary at
`/home/ian/Documents/VSCodeProjects/OpenPolaris/tools/cli-probe/build/install/cli-probe/bin/cli-probe`
that can do read-only protocol probes against the live gimbal
(`192.168.0.1:9090`). If you need a baseline of what the gimbal
firmware currently reports, that tool will give you a definitive
answer in seconds without flashing anything.

Example:

```bash
tools/cli-probe/build/install/cli-probe/bin/cli-probe send 286 1 192.168.0.1 9090
# -> 286@manufacturer:ricoh imaging company, ltd.;model:pentax k-3 mark iii;state:1;...
```

## What I will NOT do while you work

- I will not build or deploy any new firmware
- I will not modify any files on the gimbal
- I will not run the patcher docker build
- I will not change any libgphoto2 source

I will, however:
- Run more protocol probes if you tell me which codes to check
- Re-run the liveBurst gradle task and capture the canonical pre-camera
  set
- Run liveListen for 30-60s to capture push events
- File any cross-repo issues if I find something new
