# Comparing Notes with the vyskocil/Polaris Wiki

A plain-English write-up of how another open-source team's understanding of the
Benro Polaris gimbal's wire protocol lines up with — and differs from —
ours. The team in question is Vladimir Vyskocil, whose repo lives at
<https://github.com/vyskocil/Polaris> and whose wiki is at the same URL plus
`/wiki`.

If you want the full table-by-table breakdown and every conflict documented,
read [`VYSKOCIL-WIKI-REVIEW-2026-09-07.md`](VYSKOCIL-WIKI-REVIEW-2026-09-07.md).
This document is the short version: what we agreed on, what we didn't, and
what we should send back.

## What the wiki is, in one paragraph

It's two pages. The first is a command-by-command reference for the wire
protocol (TCP `192.168.0.1:9090`), listing about 30 codes with payloads and
expected responses. The second is three open questions the wiki author
couldn't answer from their own captures. There's also a Python script
(`polaris_stellarium.py`) that talks to the gimbal so a planetarium app
called Stellarium can drive it. That's it — small, focused, and the only
public third-party protocol implementation we know of outside of our own.

## What we agreed on (the boring 80%)

Most of the codes we both document look the same on the wire. Tracking on
(`531`), pointing slews (`519`), status reads (`284`), the AHRS gate
(`520`), the alignment steps (`530`), battery (`778`), SD card (`775`),
Wi-Fi band (`802`), the OMS push (`824`), the unsolicited quaternion stream
(`518`) — all of these line up. We have live captures; they have live
captures; the bytes match. That's reassuring, but it's not news we needed.

The wiki lists some camera codes (`271`, `272`, `305`, and a 545/546 pair for
the L-bracket direction). We've catalogued the same codes but with
**different framing** (see the next section). So agreement isn't universal.

## What we don't agree on (the interesting 20%)

This is the bit worth paying attention to. Six codes have a real conflict
between the wiki and our evidence. For each, our rule is: **don't import
the wiki's framing into our code or docs until we hardware-test it on our
own gimbal.** That keeps us honest.

| Code | Wiki says | We say | Why it matters |
|---|---|---|---|
| **271** | Camera Panorama message — `step:11;` | Image-size set — `imgSize:N;` | A real third-party app interpreting our framing would send the wrong payload. |
| **272** | Camera Lapse message — `step:10;` | Image-format read — `imgFmt:N;#` | Same kind of conflict. |
| **305** | Camera HG message — `step:2;` | Code is catalogued; no confirmed payload | We can't tell which side is right yet. |
| **797** | Physical-limits error — `797@errorCode:-1203;` | SD-card scan-complete ack — `797@..;#` | If the wiki is right, our parser is missing an error frame. If we're right, the wiki is misreading a normal ack. |
| **799** | Password message processor | Cellular state read | The firmware decompile we did (looking at the unstripped binary) names this `SP_GET_CELLULAR_STATE`, which matches what the stock app does on our gimbal. The wiki's password framing doesn't fit our evidence. |
| **808** | "Create Connect Context" — returns `ret:0;`, **drops the connection after a couple of minutes if you don't send it** | Firmware version — **doesn't reply at all on our gimbal** | The biggest single conflict. Either the wiki describes a newer firmware where 808 *does* reply, or one of us is talking about the wrong code. |

The full table is in [PROTOCOL.md §7](PROTOCOL.md#7-where-another-teams-notes-disagree-with-ours).
We are **not** claiming the wiki is wrong — we're saying we don't know yet,
and we're flagging it as something to test.

## What we could send upstream

This is where we have genuine value to add. There are several things we
figured out by decompiling the gimbal's own application binary (the 24 MB
ELF file inside the firmware) that the wiki author — working only from
captures — couldn't see:

1. **The 525 temperature push is a per-mount serial.** The wiki says "not yet
   known if the value is the same for all Polaris." We can see that the
   envelope (e.g. `a509ca361d0000265a`) is stable per device. Three captured
   values from different firmware builds are in
   [`POLARIS-FUNCTIONS-REPORT.md`](POLARIS-FUNCTIONS-REPORT.md).

2. **The 536 half-speed toggle is inverted.** `halfSpeed:0` enables, `halfSpeed:1`
   disables. The wiki doesn't mention this. It's a well-known footgun — easy to
   spend an evening wondering why your mount won't slow down.

3. **Camera payload grammars for codes 258–279.** The wiki skips from 305
   straight to 513 and never covers the camera codes. We have payload
   formats for ISO, white balance, aperture, EV, shutter, focus, image size
   and format, color profile, and capture mode, plus the timelapse step
   payloads (steps 4, 6, 7, 13). The numeric code mapping is inferred (we
   couldn't get an APK to confirm the code numbers), but the payload
   grammar is real.

4. **The firmware-update wire sequence.** Codes 784, 794, 795, 810, 811, 812
   in order, with chunk binary framing. The wiki doesn't mention firmware
   update at all.

5. **Inside the firmware itself.** Decompiling the unstripped binary shows
   the firmware has:
   - Four "silent tracking skip" failure paths — the goto returns success
     while tracking silently doesn't start.
   - No watchdog, no drift correction, no Kalman/EKF sensor fusion (just
     Madgwick/Mahony).
   - Fine per-axis rate control at the gimbal UART layer, but the tracker
     never uses it.
   - A full astrometry library (VSOP87, ELP, SGP4/SDP4, Chap95 planets) —
     the math is already in the device.

This is upstream-of-wire-protocol stuff. We can't be sure the wiki author
wants it on the wiki itself, but it would answer a lot of "why does the
protocol behave like this?" questions.

## Answers we have to their open questions

Their wiki's "Open points and questions" page lists three things they
don't know. We can answer them, at least partially:

1. **Does multi-star alignment improve pointing?** Reading the firmware, the
   alignment routine has a `SP_CalAuMultiStar` variant, but the stock app
   only ever calls `SP_CalAuOneStar` with `num:1`. **Best guess: the
   protocol supports multi-star alignment, but the shipped app doesn't
   exercise it.** Worth testing on hardware.

2. **What does `530 num > 1` do?** Same as above — the field is parsed but
   not used.

3. **What do `531@ret:2` and `531@ret:3` mean?** `ret:2` is "tracking
   paused" (the precision-move state machine sets this when a manual move
   starts). `ret:3` is "celestial alignment not done" (the startup
   handshake reports this until the alignment is complete).

## What we are NOT claiming

A few things in the wiki look plausible but we can't verify without
hardware tests, so we are not adopting them:

- The `797@errorCode:-1203;` physical-limit error frame — we have no
  capture of this on our gimbal.
- The "couple of minutes" socket-close time when 808 is omitted — wiki
  claim, no measurement on our hardware.
- The 0.1 s / 0.05 s timing window for quick moves (codes 513/514/521) —
  wiki claim, we don't ship a timed quick-move loop.

If we want to add any of these, we measure first. Then document. Then
implement.

## What happens next

For now, the only committed change is:

- `PROTOCOL.md §7` adds a one-screen summary of the divergent codes.
- This document and the longer review doc are the only places these
  comparisons live.

The next steps, when we have time and hardware:

1. **Test the divergent codes on the gimbal** — especially 808 (does it
   reply, and does omitting it really drop the socket?) and 797 (does it
   ever send an `errorCode:` frame?).
2. **Submit the §4 contributions upstream** if the wiki author wants them.
3. **Use the Python script as a cross-language test oracle** for our
   encoder, which would catch any future drift.

Until then, we keep our evidence and treat the wiki as a useful but
external second opinion.