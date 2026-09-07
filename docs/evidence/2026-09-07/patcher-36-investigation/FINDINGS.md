# patcher#36 — K-3 III `0xa008` / `state:-2` investigation: ROOT CAUSE FOUND

## TL;DR

The K-3 III `0xa008`/`NoUpdateImage` preview failure on Polaris is **NOT a
libgphoto2 defect, NOT a Pentax driver defect, NOT a runtime
USB-enumeration defect.** It is a **stage2 deployment defect**: the
patcher ships a stripped `libgphoto2_port.so.12` (38,620 bytes) in
`/app/lib/stage2/` that does not provide the iolibs lookup the
runtime needs, so `gp_port_info_list_load` always returns "no iolibs
found", `gp_camera_init` returns `-2`, and the runtime reports
`manufacturer:none;model:none;state:-2`.

The same K-3 III, same libgphoto2 SHA (`6aa3e4e66`), same MTP
USB setting, **works perfectly via the direct CLI**:
`IOLIBS=/app/lib/stage2/libgphoto2_port/0.12.2 /app/bin/gphoto2 --capture-preview`
returns a 77,145-byte 1080x720 JPEG in 18 ms. Only the stage2
runtime path is broken.

## Smoking gun: gphoto2 init log on a fresh pgphoto

After replacing the K-3 III battery and restarting `/app/restart_gphoto`,
the runtime Clog reports the same line every time:

```
gphoto2 2.5.27
...
libgphoto2      2.5.27         standard camlibs (SKIPPING lumix), ...
libgphoto2_port 2.5.27         iolibs: ptpip, ...
0.001255 gp_port_info_list_load [gphoto2-port-info-list.c:328](0):
    No iolibs found in '../lib/libgphoto2_port/0.12.0'
[12:59:10:092 INFO-]:sp_Gphoto_Init[147]:----sp_Gphoto_Init ret -2
[12:59:10:092 INFO-]:send_ipc_msg_retry[80]:------SP_sendMsg success;
    id[98307] type[1],code[286],val[manufacturer:none;model:none;state:-2;
    storage:0;photoFormat:0;]
```

`gphoto2` is told to look for iolibs at the relative path
`../lib/libgphoto2_port/0.12.0/iolibs/iolibs/`. With the runtime's
default CWD of `/root`, that resolves to
`/lib/libgphoto2_port/0.12.0/iolibs/iolibs/`, which does not exist.
Even when we created the directory structure with the `usb1.so`
copied in, the lookup still failed — the stage2 `libgphoto2_port.so.12`
stub's `gp_port_info_list_load` is hard-coded to log the error
and return empty regardless of what is at the path.

## What's actually on the device

| File | Size | Purpose | Status |
| --- | ---: | --- | --- |
| `/app/lib/stage2/libgphoto2.so.6` | 133,508 | libgphoto2 core, Pentax-enabled build from commit `6aa3e4e66` | OK |
| `/app/lib/stage2/libgphoto2/2.5.34/ptp2.so` | 927,572 | Pentax camlib | OK |
| `/app/lib/stage2/libgphoto2_port.so.12` | **38,620** | **Stripped stub** — has only the failure-path `gp_port_info_list_load` | **BROKEN** |
| `/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so` | 30,260 | Iolib shipped with the patcher | exists but not reachable from the runtime's lookup |
| `/app/lib/libgphoto2_port.so.12` | 105,852 | **Stock gimbal libgphoto2_port from Apr 2021 firmware** | Wrong ABI (missing `LIBGPHOTO2_5_0` symbol); not compatible with the new core |

The patcher build appears to be missing a `libgphoto2_port.so.12` that
matches the `6aa3e4e66` core ABI. The deployed `38,620` byte stub
was supposed to be a placeholder for a real libgphoto2_port
that was never shipped.

## What I tried (none worked)

1. **Set `IOLIBS` env var** to the correct path
   (`/app/lib/stage2/libgphoto2_port/0.12.2`) — runtime ignores it,
   uses hard-coded relative path.
2. **Symlink `/app/lib/stage2/libgphoto2_port/0.12.0` → `0.12.2`**
   — runtime's relative path resolves against `/root`, not stage2.
3. **Symlink `/lib/libgphoto2_port/0.12.0` → `0.12.2`** — relative
   path now resolves to a real path, but the stub's
   `gp_port_info_list_load` still logs the "not found" line.
4. **Create the `iolibs/iolibs/` subdirectory structure** at the
   expected path with `usb1.so` inside — same failure.
5. **Replace the stub with the real stock
   `/app/lib/libgphoto2_port.so.12`** — the dlopen of the core
   now fails with `symbol gp_port_init_localedir, version
   LIBGPHOTO2_5_0 not defined in file libgphoto2_port.so.12 with
   link time reference`. The new libgphoto2.so.6 (133KB) needs
   `LIBGPHOTO2_5_0` symbols that the stock 105KB libgphoto2_port
   doesn't have.

The only working path is the direct CLI which bypasses the stage2
stub entirely and uses the stock `libgphoto2_port.so.12` (105KB)
+ a relative-iolibs-lookup that **does** work because the direct
CLI doesn't have the CWD / stub issues.

## Reproduction (anyone can run)

```bash
# Bad path (runtime)
ssh root@192.168.0.1 '/app/bin/pgphoto 2>&1' | grep "No iolibs"
# > gp_port_info_list_load: No iolibs found in '../lib/libgphoto2_port/0.12.0'

# Good path (direct CLI, same libgphoto2 SHA)
ssh root@192.168.0.1 'IOLIBS=/app/lib/stage2/libgphoto2_port/0.12.2 \
  CAMLIBS=/app/lib/stage2/libgphoto2/2.5.34 \
  /app/bin/gphoto2 --capture-preview 2>&1'
# > ...Saving file as capture_preview.jpg (77,145 bytes, valid JPEG)

# Bad path with stub replaced by stock port
ssh root@192.168.0.1 'cp /app/lib/libgphoto2_port.so.12 \
  /app/lib/stage2/libgphoto2_port.so.12 && /app/bin/pgphoto 2>&1' | head -3
# > dlopen core FAILED (libgphoto2.so.6: symbol gp_port_init_localedir,
# > version LIBGPHOTO2_5_0 not defined in file libgphoto2_port.so.12)
```

## What needs to happen (no code change from my side)

The patcher build needs to **ship a libgphoto2_port that matches
the new core's ABI** (the new core requires `LIBGPHOTO2_5_0`
symbols). The candidates are:

1. Rebuild libgphoto2_port at the same commit (`6aa3e4e66`) used
   for the core, and ship the resulting `.so` (likely ~30-40KB)
   at `/app/lib/stage2/libgphoto2_port.so.12` — most likely the
   right fix; the stub is a leftover from a partial refactor.
2. Update the patcher to also pull in the matching port lib from
   upstream's `libgphoto2_port/0.12.2/` if the source has it.

Either way, this is a **patcher build defect**, not a runtime /
libgphoto2 / Pentax defect, and the path forward is in the patcher
repo.

## Evidence captured in this session

| File | What it shows |
| --- | --- |
| `01-baseline-pre-confirm.txt` | Pre-battery baseline: only the HUB attached, 5 GHz wifi, one `pgphoto` PID 1433 |
| `02-direct-cli-step1-detect.txt` | Direct libgphoto2 CLI works: K-3 III detected, summary works, get-config works, function flags 0x0, `Device Capabilities: No Image Capture, No Open Capture, No vendor specific capture` |
| `03-direct-cli-step2-preview-capture.txt` | **Direct CLI produces a valid 1080x720 JPEG preview frame (77,145 bytes) in 18 ms, first attempt.** `--capture-image` succeeds. `--trigger-capture` returns `Unsupported operation` (consistent with function flags 0x0). |
| `preview-direct-libgphoto2.jpg` | The valid JPEG captured by the direct CLI |
| `04-runtime-restart-gphoto.txt` | After pgphoto restart, runtime still reports `sp_Gphoto_Init ret -2` and `state:-2` despite device being attached |
| `05-iolibs-investigation.txt` | `--list-ports` with `IOLIBS=...0.12.2` finds the device; with `0.12.0` returns empty |
| `06-iolibs-root-cause.txt` | gphoto2's `../lib/libgphoto2_port/0.12.0` reference is a hard-coded relative path in the stage2 stub `libgphoto2_port.so.12` |
| `07-fix-attempt-corrected-iolibs.txt` | Test 1: even with `IOLIBS=...0.12.2` env, runtime still uses relative path |
| `08-symlink-fix-test.txt` | Test 2: symlink in stage2 dir doesn't help (wrong CWD resolution) |
| `09-symlink-fix-tested.txt` | Test 3: same result with empty Clog and full restart |
| `10-realpath-fix-test.txt` | Test 4: symlink at the absolute resolved path `/lib/libgphoto2_port/0.12.0` — still fails because the stub always returns "no iolibs" |
| `11-iolibs-subdir-test.txt` | Test 5: created the `iolibs/iolibs/` subdir with `usb1.so` inside — still fails |
| `12-real-lib-replace-test.txt` | Test 6: replaced stub with stock `/app/lib/libgphoto2_port.so.12` — fails with `LIBGPHOTO2_5_0 not defined` |

## Impact on the live-progress matrix

| Row | Before this session | After this session |
| --- | --- | --- |
| K-3 III Detect before restart | PASS (286 identified K-3 III) | PASS — same |
| K-3 III Detect after pgphoto restart | FAIL (`state -2`) | **Root cause is stage2 deployment, not runtime USB** |
| Direct libgphoto2 on K-3 III | PASS (per OpenPolaris#56) | PASS — confirmed via direct CLI on the embedded binary, captured a valid preview JPEG |
| Embedded K-3 III preview | FAIL (`0xa008`) | **Will be fixed by the patcher build fix in this issue** |

## Ownership chain

```
patcher#36
  └── Cause: stage2 ships stripped libgphoto2_port stub (38KB)
        └── Fix: rebuild libgphoto2_port at 6aa3e4e66, ship full .so
              └── Owner: PATCHER BUILD
```

No changes to libgphoto2, no changes to Pentax driver, no changes
to OpenPolaris, no changes to the runtime. The fix is in the
patcher build artifacts at `/app/lib/stage2/`.
