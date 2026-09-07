# Agent handoff — overview

**Date**: 2026-09-07 14:00
**From**: OpenPolaris feature-test session
**Audience**: the next agent in the rotation (likely the libgphoto2
agent or the patcher agent, depending on which open issue they pick
up first)

## The three-agent contract

| Agent | Repo | What they own | What I (OpenPolaris) will keep doing |
|---|---|---|---|
| **OpenPolaris** (this session) | ian-morgan99/OpenPolaris | Desktop app, UI, protocol client, simulator, cross-cutting | Protocol sweeps, cross-verification, evidence collection, GitHub issue filing, runtime observation (read-only SSH) |
| **libgphoto2** | ian-morgan99/libgphoto2 | libgphoto2 source (camlibs/ptp2/pentax-utils, library.c, pentaxmodern.c, tests), Pentax driver bug fixes, upstream readiness | Prompt protocol queries on demand, capture more evidence if they need it |
| **patcher** | ian-morgan99/benro-polaris-firmware-patcher | FwPkt build, install_stage2.sh, runtime configuration, iolibs-lookup mechanism, restart_gphoto wrapper, HDMI geometry | Same: prompt protocol queries, evidence, runtime observation |

I will not (in this session):
- Run the patcher docker build
- Modify any files on the gimbal
- Modify any patcher or libgphoto2 source

I will (in this session):
- Run more protocol probes if asked
- Re-run `./gradlew :tools:cli-probe:liveBurst -q` for the canonical
  pre-camera set
- Run `./gradlew :tools:cli-probe:liveListen -q --args="N host port"`
  for push capture
- File cross-repo issues if I find something new
- Push provenance-verified artifacts (e.g., FwPkt(1).zip) to
  long-term storage if needed

## The current state of the world (2026-09-07 14:00)

| Layer | State |
|---|---|
| **Gimbal firmware** | 2026-09-07-k1ii-k3iii-candidate installed (appfs `d745fe16` on FwVer 4.0.0.32 base + libgphoto2 6aa3e4e66 Pentax patches) |
| **Gimbal K-3 III** | Connected (lsusb 25fb:0189), vendor mode enabled, state:1 via protocol 286 |
| **Gimbal runtime** | BROKEN: /app/bin/ empty, libgphoto2_port iolibs lookup wrong, .stub.bak files are no longer true backups |
| **Protocol layer (9090)** | HEALTHY: codes 286, 770, 775, 778, 780, 802, 547, 543, 537, 539, 541 etc. all respond with expected payloads |
| **Live view (8080)** | NOT LISTENING (because pgphoto never starts) |
| **Port 80 (lighttpd)** | HEALTHY: returns 403 (no index) and 404 (no /preview) — same as stock, no 8080 redirect |
| **OpenPolaris repo** | 6 commits ahead of origin today (all pushed). Issue #66 = protocol map tracking. 30 issues total. |
| **patcher repo** | Issues #38/#39 (the gimbal runtime defects) + 12 other open. 30 issues total. |
| **libgphoto2 fork** | Issues #48/#49 (the source-reproduction tasks) + 4 other open. 30 issues total. |

## Where to read

| Topic | File |
|---|---|
| Full protocol code map (live) | [docs/evidence/2026-09-07/protocol-mapping/PROTOCOL-MAP-2026-09-07.md](../protocol-mapping/PROTOCOL-MAP-2026-09-07.md) |
| Raw transcript | [docs/evidence/2026-09-07/protocol-mapping/transcripts/cli-probe-transcript-2026-09-07-1336.txt](../protocol-mapping/transcripts/cli-probe-transcript-2026-09-07-1336.txt) |
| Feature test inventory (what's still testable) | [docs/evidence/2026-09-07/protocol-mapping/FEATURE-TEST-INVENTORY.md](../protocol-mapping/FEATURE-TEST-INVENTORY.md) |
| Handoff to libgphoto2 agent | [HANDOFF-LIBGPHOTO2.md](HANDOFF-LIBGPHOTO2.md) |
| Handoff to patcher agent | [HANDOFF-PATCHER.md](HANDOFF-PATCHER.md) |
| Session timeline | [LIVE-PROGRESS.md](../../../../LIVE-PROGRESS.md) |
| TRACKING issue on OpenPolaris | [#66](https://github.com/ian-morgan99/OpenPolaris/issues/66) |

## What we proved in this session

- The K-3 III is fully identified by the firmware (286 = state:1,
  vendor mode enabled, MTP single-session trap notwithstanding)
- The patcher version stamp (sw:6.0.0.54) is visible via 780
- The protocol layer's response to 286/770/775/780/802/etc matches the
  direct shell inspection (ls, df, cat) on the device
- The push events (525 Tempa<hex16>;) work
- The 38,620-byte `libgphoto2_port.so.12` is the real libgphoto2_port,
  not a stub (Docker build produces the same size and SHA)
- Code 789 (FILE_DELETE_ALL) is destructive and silent — flagged as a
  footgun
- The `/app/bin/` is empty on the running 2026-09-07 build
- The .stub.bak/.stock.bak files are no longer true backups (same
  SHA as deployed)

## What we did NOT prove (and which agent owns each)

| Open question | Owner |
|---|---|
| Does the K-3 III embedded preview actually work when the runtime is fixed? | patcher agent (needs install + flash) |
| Does the K-3 III -1005 (issue #37) reproduce in libgphoto2 with a directly attached K-3 III? | libgphoto2 agent (issue #48) |
| Does the WB/aperture false-failure (issue #49) reproduce in libgphoto2 with a directly attached K-3 III or K-1 II? | libgphoto2 agent |
| Does the iolibs lookup mechanism (issue #38) get fixed by changing CWD, symlinking, or patching libgphoto2_port? | patcher agent (after libgphoto2 confirms the latter is needed) |
| Does the /app/bin/ empty (issue #39) get fixed by adding a step to install_stage2.sh or by changing the FwPkt layout? | patcher agent |
| Does the desktop app render the same values I saw via cli-probe? | next OpenPolaris session (start the desktop app, walk through UI) |

## What happens if you (next agent) need to talk to the gimbal

The gimbal is reachable from this host:

```bash
ssh -o ConnectTimeout=5 -o BatchMode=yes root@192.168.0.1
```

The cli-probe is at:
`/home/ian/Documents/VSCodeProjects/OpenPolaris/tools/cli-probe/build/install/cli-probe/bin/cli-probe`

Or via gradle:
`./gradlew :tools:cli-probe:liveBurst -q` (canonical pre-camera burst)
`./gradlew :tools:cli-probe:liveListen -q --args="30 192.168.0.1 9090"` (push capture)
`./gradlew :tools:cli-probe:authSmoke -q` (handshake smoke test)

**Do not** write to /app/bin/, /app/lib/, /app/sd/normal/ — the user
stated all fixes must go via a firmware packet. See
[OpenPolaris#65](https://github.com/ian-morgan99/OpenPolaris/issues/65)
for the rationale.

**Do** read `/app/Clog.txt`, `/app/Mlog.txt`, `/app/sd/system/log/*`,
`/proc/net/tcp`, `/proc/<pid>/cmdline`, `/app/cfg/*`,
`/app/openpolaris-libgphoto2-provenance.txt` — these are all
diagnostic state that helps you without mutating anything.
