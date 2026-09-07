# vyskocil/Polaris Wiki — Cross-Reference (2026-09-07)

**External source:** <https://github.com/vyskocil/Polaris/wiki> (last wiki edit 2024-08-30 by Vladimir Vyskocil).
The wiki contains exactly two substantive pages:

1. **Polaris protocol commands** — 40-revision command-by-command reference
2. **Open points and questions** — three open protocol questions

Vyskocil's repo also ships `polaris_stellarium.py` — a Python/AsyncIO Stellarium
bridge that implements the protocol end-to-end on top of the same TCP `192.168.0.1:9090`
channel. Together, wiki + script constitute the most concise third-party protocol
implementation we know of outside of our own.

> **Reading rule.** The wiki is **another team's understanding**, derived from
> their own captures of their own gimbal. Their firmware build (sw 6.0.0.54 or
> similar) may differ from ours in ways that change the payload shapes. We treat
> each wiki claim as one of three things:
>
> - **Confirmed** — we have an independent live capture that matches.
> - **Tentative second source** — the wiki's shape is plausible and doesn't
>   contradict any of our captures; useful as a sanity check, not as a fact.
> - **Conflicting** — the wiki says one thing, our evidence says another. These
>   become open questions (see §3).

The rest of PROTOCOL.md stays anchored to *our* evidence; this doc is where the
comparison lives.

---

## 1. Quick orientation

If you only read three things, read these:

1. **§3 — Conflicting codes.** These are the codes where wiki and our captures
   disagree. Until we hardware-test the wiki's framing, we keep our entries and
   flag the wiki's as an open question.
2. **§4 — What our evidence covers that the wiki doesn't.** This is the
   material that would be a genuinely useful contribution upstream.
3. **§5 — Open questions we could answer for them.** Their "Open points"
   page lists three questions; we can answer them (at least partially) from
   our evidence.

§2 (tentative agreements) is a quick reference, not a commitment.

---

## 2. Tentative agreements (wiki as second-source sanity check)

For these codes, the wiki's payload shape is *consistent* with our captures.
We don't import the wiki's framing into PROTOCOL.md, but if you see a discrepancy
between our live capture and your reading of the wiki, the wiki is at least
plausible.

> **Caveat:** agreement on the *shape* doesn't mean agreement on the *direction*
> (client→mount vs mount→client), the *field semantics*, or the *response codes*.
> Treat this table as "the wiki is not contradicting us here," not as "we endorse
> the wiki's exact wording."

| Code | Wiki claim | Our live capture / PROTOCOL entry | Notes |
|---|---|---|---|
| 284 | `284@mode:;state:;track:;speed:;halfSpeed:;remNum:;runTime:;photoNum:;` (astro mode) | identical shape, [POLARIS-FUNCTIONS-REPORT.md §2.1](POLARIS-FUNCTIONS-REPORT.md) | Both teams observe the same fields. |
| 285 | `1&285&2&mode:<m>;#` with modes 1–10 listed | identical | Mode-list is identical. |
| 286 | `286@manufacturer:;model:;state:;storage:;photoFormat:;` | identical | Same field set. |
| 519 | `1&519&3&state:1;yaw:;pitch:;lat:;track:;speed:;lng:;` | identical | Same wire shape. |
| 520 | `1&520&2&state:{0\|1};#` | identical | Both teams agree on the gate semantics. |
| 527 | `1&527&3&compass:;lat:;lng:;` | identical | Same payload fields. |
| 530 | `step:1` to start, `step:2` to validate; `num:1` for one-star | identical | Same step semantics. |
| 531 | `1&531&3&state:;speed:;` (state 0/1; speed 0 = sidereal) | identical | Both teams observe the same. |
| 532 / 533 / 534 | precision moves; state 0=stop / 1=press / 2=long-press @+2 s | identical shape; the **two-second re-fire** is more clearly described in our PROTOCOL.md §3.2 than in the wiki | Wiki is more explicit on the 2-second re-fire; we document it too. |
| 775 | `775@status:;totalspace:;freespace:;usespace:;` | identical | |
| 778 | `778@capacity:;charge:;` | identical (and we observe `charge:2` = "on charge, no battery") | Wiki doesn't comment on the `charge=2` case, but the wire shape matches. |
| 780 | `780@hw:;sw:;exAxis:;sv:;ov:;` | identical | |
| 782 | `1&782&2&date:;time:;zone:;` (GMT+10 = `+36000`) | identical | Same encoding. |
| 802 | `802@band:;` | identical (live `band:0` = 2.4 GHz) | |
| 824 | `824@val:;` | identical | OMS push. |
| 518 | unsolicited push, two quaternions + compass + alt | identical | Same shape. |

## 3. Conflicting codes (the headline finding)

These are codes where the wiki and our evidence say different things. Until we
hardware-confirm the wiki's framing on our gimbal, we **do not import it** into
PROTOCOL.md. Each row is an open question.

### 3.1 Code 271

| | Framing |
|---|---|
| Wiki | "Unknown Camera Panorama Msg from App" — `1&271&2&step:11;` → `271@step:11;state:0;` |
| Us | `CAM_SET_IMG_SIZE` — `271@imgSize:N;` ([POLARIS-FUNCTIONS-REPORT.md §2.5](POLARIS-FUNCTIONS-REPORT.md)) |

**Status:** Conflict. Three possibilities — (a) different firmware build,
(b) different mode (panorama vs photo changes the code's role), or (c) one of
us is misreading the other direction. **Open question.**

### 3.2 Code 272

| | Framing |
|---|---|
| Wiki | "Camera Lapse Msg from App" — `1&272&2&step:10#` → `272@step:9;state:1;` |
| Us | `CAM_GET_IMG_FMT` — `272@imgFmt:N;#` |

**Status:** Same shape of conflict as 271. **Open question.**

### 3.3 Code 305

| | Framing |
|---|---|
| Wiki | "Unknown Camera HG message" — `1&305&2&step:2;#` → `305@step:2;state:0;` |
| Us | Code is catalogued; payload format not yet wire-confirmed |

**Status:** Neither side has a strong live capture; both framings are tentative.
**Open question.**

### 3.4 Code 797

| | Framing |
|---|---|
| Wiki | "Physical-limits error frame" — `797@errorCode:-1203;#` |
| Us | `FILE_SCAN_COMPLETE` ack — `797@..;#` ([POLARIS-FUNCTIONS-REPORT.md §2.7](POLARIS-FUNCTIONS-REPORT.md)) |

**Status:** Could be that the wiki describes a *different* use of 797 (the
error direction we haven't observed), or could be that we have the role wrong.
We do **not** have a `797@errorCode:-1203;` frame in our evidence base. **Until
we capture one on our gimbal, we do not add error-code parsing to the client.**
**Open question.**

### 3.5 Code 799

| | Framing |
|---|---|
| Wiki | "Password Msg Process" — `1&799&2&-100;#` → `799@ret:-1;` |
| Us | `GET_CELLULAR_STATE` — live `ret:-1;`, asymmetric with 802 ([POLARIS-FUNCTIONS-REPORT.md §4 quirk 4](POLARIS-FUNCTIONS-REPORT.md)) |

**Status:** Both sides observe the same `ret:-1;` response. But our firmware
decompile names it `SP_GET_CELLULAR_STATE`, not anything password-related.
The wiki's framing doesn't match what the stock app's connect-burst does on
our gimbal. **We keep `GET_CELLULAR_STATE`.** Open question for the wiki.

### 3.6 Code 808

| | Framing |
|---|---|
| Wiki | "Create Connect Context Request" — `1&808&2&type:0;#` → `808@ret:0;`; **"critical for the connection to remain open; otherwise, Polaris will drop the connection after a couple of minutes"** |
| Us | `SYS_VERSION` (corpus name) — **live doesn't reply on real hw** ([POLARIS-FUNCTIONS-REPORT.md §4 quirk 1](POLARIS-FUNCTIONS-REPORT.md)) |

**Status:** Major conflict. The wiki describes 808 as a connection-context
handshake that returns `ret:0;`. Our live capture on real hardware (sw
6.0.0.54) shows 808 doesn't reply at all — the smoke harness SKIPs it. Two
possibilities: (a) the wiki describes a different firmware build where 808
*does* reply, or (b) one of us is talking about a different 808 entirely.
**Open question — and the "couple of minutes" claim is unverified on our hardware.**

## 4. What we have that the wiki doesn't (genuinely useful contributions)

These are things we could send upstream that would help vyskocil (or any third
party) without us needing to import anything we're unsure about.

### 4.1 The 525 angle-bracket envelope — per-mount serial

The wiki says:

> 525 — `525@Tempa509ca...;#` is sent periodically. *It's not yet known if the
> value is the same for all the Polaris or not.*

We can answer that. The captured envelopes in our evidence vary per firmware
build but are **stable per device**:

- `a509ca361d0000265a`
- `a509ca361e0000275a`
- `a509ca361f0000285a`

([POLARIS-FUNCTIONS-REPORT.md §2.1 / §4 quirk 10](POLARIS-FUNCTIONS-REPORT.md)).
The pattern is `a509ca361<d/e/f><lowercase>0000<3-hex>` — likely a per-mount
serial envelope. **Suggestion to wiki:** the value is a per-mount serial
stable across reconnects; useful as a soft device fingerprint.

### 4.2 The 536 halfSpeed inversion (hardware-verified footgun)

The wiki is silent on the **inverted payload semantics of `536`**. We have:

> [PROTOCOL.md §3.1](PROTOCOL.md) — `536 halfSpeed:%d;` is **INVERTED** —
> app sends `halfSpeed:0` to ENABLE, `halfSpeed:1` for full speed.

This is a well-known footgun for third-party implementations. Worth a wiki
note: *"Note: payload polarity is inverted; the firmware treats 0 as on and 1
as off. Sending 1 to 'enable half speed' silently keeps the mount at full
speed."*

### 4.3 Camera-control payload grammars (codes 258–279)

The wiki's command table skips straight from 305 to 513 and is **silent on
camera codes 258–279**. We have ground-truth payload formats from the
`polestar_app` decompile:

> [PROTOCOL.md §3.4](PROTOCOL.md) — payload formats for ISO/WB/aperture/EV/
> shutter, capture-state pushes, focus, timelapse step payloads (6, 7, 4, 13),
> and the index-into-firmware-sorted-list semantics.

The numeric codes within 258–279 are inferred (we couldn't get an APK to
confirm the code numbers), but the payload formats are real. Even with the
caveat, this would expand the wiki materially.

### 4.4 Firmware-upload wire sequence (codes 784/794/795/810/811/812)

The wiki doesn't mention firmware update. We have:

> [PROTOCOL.md §3.6](PROTOCOL.md) + [CAPABILITY-GUIDE.md §4](CAPABILITY-GUIDE.md) —
> full upload sequence: `SYS_FW_UPGRADE` (810) → `FILE_UPLOAD_FW` (784) →
> `FILE_UPLOAD_CHUNK` (794 × N) → `FILE_UPLOAD_END` (795) → `SYS_FW_PROGRESS`
> (811) → optional `SYS_REBOOT` (812).

The wire-level sequence is real; the chunk binary framing (794's payload slot)
is smoke-tested on the in-process simulator only.

### 4.5 Firmware-architecture findings (deeper than any wire-protocol team can get)

> [FIRMWARE-ANALYSIS-ALPACA.md](FIRMWARE-ANALYSIS-ALPACA.md) covers what we
> found by decompiling `bin/polestar_app` (24 MB ARM ELF, unstripped, with
> DWARF debug paths):
>
> - The four "silent tracking skip" failure paths inside `sp_gotoAu.c`
> - The firmware has no watchdog, no drift correction, no Kalman/EKF fusion
> - `sp_trackAu.c` only commands full / half speed (per-axis fine rate control
>   exists at the gimbal UART layer but is unused by the tracker)
> - The full astrometric library (VSOP87, ELP, SGP4/SDP4, Chap95) is already
>   in the device

This is upstream-of-wire-protocol knowledge. The wiki author doesn't have
firmware access; this would expand their understanding of *why* the protocol
behaves the way it does.

### 4.6 The azimuth sign convention for 519

The wiki documents `519` as `state:1;yaw:;pitch:;lat:;track:;speed:;lng:;`
without explaining the **yaw sign convention**. Our `polaris_stellarium.py`
script (from the wiki author's repo) and our `AstroMath.kt` independently
agree on `polaris_az = 360 - az if az > 180 else -az` — i.e. yaws wrap into
(-180, 180]. The wiki's own code contains the formula; it's just not in the
protocol-commands page. Worth adding a sentence to save the next implementer
a day of debugging.

## 5. Open questions from the wiki that we can answer (partially)

Three questions are listed on `Open-points-and-questions`. Our evidence
addresses them:

### 5.1 Does repeating 530 with multiple stars improve alignment?

We have no direct experiment. Our connect-burst analysis shows the stock app
only sends `step:2` with `num:1`. The firmware's `SP_CAL_AU_STA_*` state
machine accepts `num:N` but the rest of the firmware never calls into it
for `num > 1`. **Best read of the code: multi-star alignment is supported in
the protocol but not exercised by the stock app.**

### 5.2 What does `530 num > 1` do?

See §5.1. The `num:` field is parsed (we see it in
[POLARIS-FUNCTIONS-REPORT.md](POLARIS-FUNCTIONS-REPORT.md)). The alignment
routine's `SP_CalAuOneStar` wrapper is what the app calls; the multi-star
`SP_CalAuMultiStar` variant exists but is dead code in the firmware build we
analysed. Worth a hardware probe.

### 5.3 What do `531@ret:2` and `531@ret:3` mean?

- `ret:2` = "tracking paused" — consistent with the precision-move state
  machine in our docs and the wiki.
- `ret:3` = "celestial alignment not done" — consistent with the startup
  sequence in the wiki and our PROTOCOL.md.

Both are documented as observed. The firmware's intent is a 4-value enum in
`sp_trackAu.c`; our reading matches.

## 6. Wiki claims we explicitly do NOT import

The following wiki items appear in an earlier draft of this review as
"gains." We have walked them back because we lack independent
confirmation:

- **`797@errorCode:-1203;`** as a physical-limit error frame. We have no
  capture of this on our gimbal. Until we do, **the client does not parse
  `797@errorCode:`**. Adding it now would be a guess.
- **"Polaris will drop the connection after a couple of minutes" without
  `808`.** Wiki-only claim. Could be true on a newer firmware build; could
  not. Until we measure it on our gimbal, we **do not** add a session
  timeout shorter than the protocol's existing 2 s command timeout.
- **0.1 s per `513`/`514`/`521` command, 0.05 s cadence** (quick-move timing).
  Wiki-only claim. We don't ship a timed quick-move loop, so this doesn't
  affect client behavior today; if we add one, we'll measure it on hardware
  first.
- **`525` push carries per-mount serial.** This is plausible from our captures
  (the envelopes vary per mount) but we don't have enough captures across
  multiple physical gimbals to be sure. We list it as **a suggestion for the
  wiki** rather than as our own claim.
- **Wiki's framing of `271`, `272`, `305`, `797`, `799`, `808`** (see §3).
  All are conflicts, not facts.

## 7. Action items (only what we're confident enough to commit to)

| # | Action | Status |
|---|---|---|
| 1 | Add `§7 Where another team's notes disagree with ours` to PROTOCOL.md, pointing here | **Done** — see [PROTOCOL.md §7](PROTOCOL.md#7-where-another-teams-notes-disagree-with-ours) |
| 2 | Revise this doc to lead with divergences (not agreements) | **Done** — this document |
| 3 | Add the §4 contributions (525 envelope, halfSpeed inversion, camera grammars, FW-upload sequence) to the wiki upstream | Not started — needs user approval before submitting to a third-party repo |
| 4 | Hardware-verify the §3 divergences on our gimbal before deciding which side is right | Not started — needs hardware + user time |
| 5 | Quantify the "couple of minutes" 808 socket-close claim with a real test | Not started — needs hardware |
| 6 | Wire `polaris_stellarium.py` payloads into a cross-language test oracle | Not started — useful but lower priority |