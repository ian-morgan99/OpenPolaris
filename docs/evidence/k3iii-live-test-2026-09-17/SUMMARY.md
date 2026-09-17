# K-3 III on-Polaris qualification — 2026-09-17

## Provenance and constraints

- Route: `192.168.0.1 dev wlp8s0 src 192.168.0.4`.
- Camera: USB `25fb:0189`; protocol identifies `pentax k-3 mark iii`, state 1.
- Deployed libgphoto2: `121675124e173da1864421acebea8e20c851c827`.
- Deployed patcher: `cddafb6f087303e3d6d16850e0ab10f962cccbf4`.
- Deployed build: `6.0.0.54.7`; pgphoto PID 7936 remained alive.
- OpenPolaris checkout: local `7e5b2e7`, one commit behind `origin/main` (`57dd1a0`).
- A second client at `192.168.0.2` held both 8080 and 9090 throughout the
  camera tests. Results are therefore multi-client field evidence, not a clean
  single-client baseline.

## Results

| Capability | Result | Evidence |
|---|---|---|
| Repository regression | PASS | `./gradlew test --no-daemon`: 137 tasks, BUILD SUCCESSFUL |
| Read-only live smoke | PASS exercised / 37 safely skipped | 24 correlated passes, 0 failures; camera identified correctly |
| ISO set/read/restore | PASS | index 0 -> 1 -> 0; every set returned `ret:0` and read-back matched |
| EV set/read/restore | PASS | index 15 -> 16 -> 15; read-back matched |
| Shutter set/read/restore | PASS | index 26 -> 27 -> 26; read-back matched |
| Aperture set/read/restore | PASS | index 17 -> 18 -> 17; read-back matched |
| White balance read/write | FAIL / NOT WRITTEN | option list returned, but current `V:` was empty, so no safe restore value existed |
| Preview control | PASS | 291 OFF/ON `ret:0`; 292 read-back 0 then 1 |
| Preview data plane | PASS | bounded 20 s stream: 710,660 bytes, 10 complete JPEGs, 11 boundaries |
| Manual focus 311 | PASS at protocol/runtime layer | near `adj:-1` and far `adj:1` each returned explicit `ret:0`; physical direction was not independently observed by Codex |
| Focus jog 262 | PARTIAL | both directions accepted; stop acknowledgements were delayed/interleaved and final stop was not correlated inside 3 s |
| Explicit autofocus | NOT IMPLEMENTED / NOT TESTED | OpenPolaris exposes manual 262/311 paths but no distinct autofocus-drive function; no guessed command was sent |
| Still capture | PASS | exactly one new `SP_0052.dng`, 28,444,933 bytes; state 1 -> 4 -> file event 773 -> state 0 |
| Captured-file integrity | PASS | TIFF/DNG header `49 49 2a 00`; MD5 `b7d6b1dc764c8b50cdb5da2e186a96a4` |
| Camera/runtime survival | PASS | USB remained present and pgphoto PID 7936 remained alive after all exercised functions |
| Radio health | FAIL | Broadcom bookkeeping error count rose from 750 before preview control to 1,258 by final health check; errors continued while the other `192.168.0.2` preview/control client remained connected |

## Important observations

- Capture completion is now end-to-end for this one shot; it is not merely an
  accepted shutter frame. The generated DNG exists on Polaris storage and has a
  valid header/hash.
- The capture log internally announced an initial `.jpg` path before completing
  as `.dng`. Consumers must use the final file event rather than the provisional
  path.
- The remaining radio failure cannot be attributed to this client alone because
  `192.168.0.2` continuously held preview and control sessions.
- No firmware update, reboot, USB reset, SD format, media deletion, or guessed
  camera command was performed.

## Evidence files

- `baseline-device.txt`
- `read-only-smoke.txt`
- `camera-read-matrix.txt`
- `parameter-roundtrip.txt`
- `preview-control.txt`, `preview-stream-analysis.txt`
- `focus-commands.txt`, `post-focus-device.txt`
- `capture-one.txt`, `post-capture-device.txt`
- `active-clients.txt`, `final-health.txt`
