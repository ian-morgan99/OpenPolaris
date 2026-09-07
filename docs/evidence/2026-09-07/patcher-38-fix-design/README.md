# Patcher #38 fix design — two-path iolibs lookup

**Date**: 2026-09-07 15:00
**Author**: this agent (OpenPolaris session)
**Subject**: Patcher #38 — fix the stripped 38KB libgphoto2_port.so.12
stub and the two-path runtime iolibs lookup.

## The problem (recap)

The `install_stage2.sh` script in the patcher:
1. Builds `libgphoto2_port.so.12` from the same libgphoto2 source
   as the core (commit `6aa3e4e66`).
2. Copies it to `/app/lib/stage2/libgphoto2_port.so.12`.
3. Copies the freshly-built `usb1.so` to
   `/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so`.
4. Sets `IOLIBS=$D/libgphoto2_port/0.12.2` and `CAMLIBS=...` in
   the wrapper env.
5. Backups the STOCK `ptp2.so` and `usb1.so` at the stock
   camlib/iolib paths to `.prestage2.bak`, then overwrites
   them with the fresh versions.

**Two failures:**

### Failure 1: the 38,620 B stub is a stripped symlink-table stub

The build pipeline appears to use `strip --strip-unneeded` which
removes the `LIBGPHOTO2_5_0` versioned symbols the freshly-built
2.5.34 core needs. The result is a `libgphoto2_port.so.12` that
is **too small to contain the symbol table** the core looks up.

When the freshly-built core (133,508 B, with full
`LIBGPHOTO2_5_0` symbols) tries to dlopen the stripped port:
```
dlopen core FAILED: symbol gp_port_init_localedir, version
LIBGPHOTO2_5_0 not defined in file libgphoto2_port.so.12
```

If you replace it with the stock 105,852 B lib (which has the
right versioned symbols but is the **wrong ABI** for 2.5.34),
the core fails for a different reason.

**The fix**: the build pipeline must use
`strip --strip-debug` (NOT `--strip-unneeded`) for
`libgphoto2_port.so.12`, OR better, use `objcopy` to remove
only the `.debug_*` and `.note.gnu.build-id` sections. The
resulting file should be **the same size as the
unstripped build** minus a few KB for the debug info.

Expected size: ~70-90 KB (vs. current 38,620 B).

### Failure 2: the iolibs lookup happens twice, with different paths

The 14:20 live evidence shows:
- The trampolined core dlopen path works
  (`/proc/PID/maps` shows `/app/lib/stage2/libgphoto2.so.6`
  at 0xb692b000)
- But a **child process** (gphoto2 CLI?) loads the
  **stock** `/app/lib/libgphoto2.so.6` 2.5.27 from
  `/app/lib/libgphoto2.so.6`, NOT the trampolined one
- The stock 2.5.27 looks for iolibs at
  `../lib/libgphoto2_port/0.12.0/iolibs/iolibs/` which
  doesn't exist
- This breaks the stock path independently of the
  freshly-built trampolined path

**The fix**: the wrapper must `cd /app/lib/stage2` BEFORE
execing `pgphoto.stage2ondisk`, so the relative `../lib/...`
path resolves correctly. OR the wrapper must set
`IOLIBS=/app/lib/stage2/libgphoto2_port/0.12.2` and the
runtime must respect it.

The current wrapper sets `IOLIBS` but `install_stage2.sh`'s
step 2b places the fresh `usb1.so` at the **stock** iolib
path too — so there's a path-mismatch between where the
files are and where the stock child process looks.

### Combined fix

```diff
# container/ondisk/pgphoto.wrapper.in
 D=/app/lib/stage2
+cd "$D" 2>/dev/null || true   # <-- ADD THIS
 export CAMLIBS=$D/libgphoto2/@CAMLIBS_VERSION@
 export IOLIBS=$D/libgphoto2_port/@IOLIBS_VERSION@
 export LD_LIBRARY_PATH=$D:/app/lib
```

AND change the build pipeline to:
```diff
# container/build_ptp2.sh (or wherever port is stripped)
-strip --strip-unneeded libgphoto2_port.so.12
+strip --strip-debug libgphoto2_port.so.12
# OR
+objcopy --remove-section=.debug_* --remove-section=.note.gnu.build-id libgphoto2_port.so.12
```

## Why both fixes are needed

- Fix 1 alone (strip-debug) makes the trampolined path work.
- Fix 2 alone (cd to stage2) doesn't help if the port is the
  stub.
- Both together: the freshly-built core dlopens a properly-
  versioned port (fix 1), and the stock child process either
  uses the trampolined path or finds iolibs at the correct
  relative path (fix 2).

## What the fix does NOT do

- Does NOT change the running state. The new FwPkt.zip
  must be flashed.
- Does NOT fix patcher#39 (`/app/bin/` empty). Separate.
- Does NOT add a K-1 II vendor-mode test. Separate.

## Acceptance criteria

After flashing a fixed FwPkt.zip on a K-3 III (charged battery):

1. `ls -la /app/lib/stage2/libgphoto2_port.so.12` shows
   size > 60,000 B (not 38,620 B).
2. `nm -D /app/lib/stage2/libgphoto2_port.so.12 | grep
   LIBGPHOTO2_5_0` shows the versioned symbols.
3. `/app/restart_gphoto` does not emit
   "No iolibs found in '../lib/libgphoto2_port/0.12.0'".
4. `sp_Gphoto_Init` returns 0 (not -2).
5. Camera info (286) reports `state:1` for K-3 III in MTP mode.
6. `IOLIBS` and `CAMLIBS` env vars are visible in
   `/proc/PID/environ` of the running pgphoto.

## Time estimate

- Build pipeline change: 30 min (one sed in build_ptp2.sh
  + verify with `nm -D`)
- Wrapper change: 5 min (one `cd` line)
- Rebuild FwPkt.zip: 1-2 hours
- Flash + verify on K-3 III: 30 min
- Total: ~3 hours, requires physical K-3 III with charged
  battery

## Cross-references

- patcher#38 (this issue)
- patcher#39 (/app/bin/ empty) — separate, fix in design
  doc
- patcher#36 (K-3 III preview 0xa008) — root cause chain
- OpenPolaris#56 (umbrella qualification)
- docs/evidence/2026-09-07/patcher-36-investigation/FINDINGS.md
  — the 12-step reproduction

## What the patcher agent should do

1. Read this design doc
2. Apply the two one-line fixes
3. Rebuild FwPkt.zip with the same provenance:
   `libgphoto2@6aa3e4e66 + patcher@<current>`
4. Document the new SHA256 in this design doc
5. Hand back to OpenPolaris agent for K-3 III / K-1 II
   qualification
