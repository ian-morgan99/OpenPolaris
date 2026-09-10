# Camera parity: junior-agent implementation guide

This guide turns GitHub issues #62 and #63 into small, reviewable tasks. Work on
**one feature at a time**. Do not expose a control on physical hardware until
its complete request and response contract is represented and tested.

## What evidence is available

The local APK is:

```text
/home/ian/Downloads/BenroConnect_1727595281455.apk
```

PrivateResearch currently contains the extracted constant table and audit, not
a checked-in full JADX source tree:

```text
/home/ian/Documents/VSCodeProjects/PrivateResearch/openpolaris-research/Benro-Connect/
  polaris-cmd-extracted.txt
  CAMERA-MAP-COMPARE.md
  README.md
  extract-polaris-cmd.py
```

The APK itself contains much more than the opcode inventory. JADX exposes the
official app's `sendOrder(code, subtype, payload)` construction methods and its
response parsers. Therefore most wire contracts can be implemented from static
code evidence. Physical testing is still needed to establish actual behaviour,
timing, supported options, and differences between the K-3 III and K-1 II.

Create a disposable decompile for each audit. Do not commit vendor source:

```bash
JADX_OUT=$(mktemp -d /tmp/openpolaris-benro-jadx.XXXXXX)
/home/ian/tools/jadx/bin/jadx --no-res --threads-count 8 \
  -d "$JADX_OUT" \
  /home/ian/Downloads/BenroConnect_1727595281455.apk
```

JADX may report errors in unrelated classes while still producing the camera
classes needed here. The primary Polaris sources are:

```text
$JADX_OUT/sources/com/snoppa/application/constant/polaris/PolarisCMD.java
$JADX_OUT/sources/com/snoppa/polaris/singleton/PolarisOrderCommunication.java
```

Also inspect every call site for the selected method. Call sites establish the
meaning and valid range of arguments that a transport method alone may not
explain:

```bash
rg -n 'SP_FOCUS_STACK|SP_HDR' "$JADX_OUT/sources/com/snoppa/polaris"
rg -n 'sendOrder\(270|parseSP_FOCUS_STACK' \
  "$JADX_OUT/sources/com/snoppa/polaris/singleton/PolarisOrderCommunication.java"
```

## Important distinction

- **Opcode inventory** answers: “What does code 270 mean?”
- **Request contract** answers: “Which subtype and exact fields are sent for
  every focus-stack step?”
- **Response contract** answers: “Which fields and states does the app parse?”
- **Runtime qualification** answers: “Does this firmware/camera execute it,
  with valid output and recovery?”

The decompile normally supplies the first three. Hardware supplies the fourth.
Do not call an opcode-only mapping a completed feature, but do not wait for a
packet capture when the exact construction and parser are readable in code.

## Current OpenPolaris baseline

Commit `8042233` adds the canonical `Codes.BenroCamera` registry and a guarded
qualification surface for five settings:

| Setting | SET | INFO | Request field |
|---|---:|---:|---|
| ISO | 258 | 265 | `iso:<index>;` |
| White balance | 259 | 266 | `wb:<index>;` |
| EV | 260 | 267 | `ev:<index>;` |
| Shutter | 261 | 268 | `shutter:<index>;` |
| Aperture | 276 | 275 | `fNum:<index>;` |

These use GET-before, SET, GET-after and update displayed state only after a
matching read-back. They are intentionally behind Camera qualification mode and
a second acknowledgement in the Camera pane.

The old inferred `CAM_GET_*` / `CAM_SET_*` names remain temporarily for source
compatibility. They are not ground truth and must not be used for new work.

## One-feature workflow

Use this sequence for each feature. A pull request or commit should normally
contain only one feature contract.

1. **Claim one row in issue #63.** Record feature, owner, start time, and camera
   body if hardware will be used.
2. **Find the opcode constant** in `PolarisCMD.java` and
   `polaris-cmd-extracted.txt`.
3. **Find every request method** in `PolarisOrderCommunication.java`. Record
   exact opcode, subtype, field names, separators, step values, and optional
   branches. Preserve capitalization such as `fNum`.
4. **Find every response parser** (`parseSP_*`) and record all fields, state
   values, callbacks, and terminal/error states.
5. **Inspect all call sites** to determine argument meaning, permitted ranges,
   ordering, and UI preconditions. Do not infer these from parameter names alone.
6. **Write a compact derived contract** in `docs/PROTOCOL.md`. Paraphrase the
   behaviour and record interoperability facts; do not copy large vendor-source
   blocks.
7. **Add typed constants and models.** Use `Codes.BenroCamera`; do not generate
   sequential GET/SET pairs.
8. **Implement the controller as a state machine** when a feature has multiple
   `step:` messages. Do not expose a bag of raw step buttons as the user feature.
9. **Parse replies before changing UI state.** A send completing is not proof
   that the camera accepted or completed the operation.
10. **Add protocol contract tests** for every outgoing step and parser tests for
    success, error, malformed, out-of-order, and terminal replies.
11. **Add simulator/replay coverage** using recorded or hand-authored protocol
    frames derived from the contract.
12. **Run software gates** listed below.
13. **Expose it in qualification mode**, with clear progress, cancel/recovery,
    and raw evidence. Keep it out of the normal surface until hardware passes.
14. **Qualify K-3 III and K-1 II separately.** Restore original settings and
    attach the evidence to issue #63.
15. **Promote the feature** only after the issue row contains no unexplained
    state and failures do not display as success.

## Recommended task order

Start with bounded, reversible operations and leave complex shooting workflows
until the shared state-machine pattern is proven.

1. Complete option enumeration and read-back for ISO, WB, EV, shutter, aperture.
2. Focus set (262) and focus adjustment (311).
3. Image format query (282), exposure time (298/299), interval type (306/307),
   and control mode (296/297).
4. Preview ON/GET/OFF (291/292), including valid JPEG and cadence checks.
5. Video/photo status (263/264), preserving the separately verified still
   capture subtype-4 contract.
6. Delayed shot (272).
7. Focus stack (270).
8. HDR (280).
9. Panorama (271).
10. Sun shot (277), PLC (283), people removal (289), and holy grail (305).

Do not mix file/system codes 770-825 into a camera-feature change. Their audit
belongs in a separate bounded series, especially destructive operations.

## Multi-step workflow checklist

For each workflow, inventory every official method, not only `START`. The
decompile shows examples such as `step:1;`, `step:2;`, etc., often including
preview, point submission, pause, completion, cancel, and state-query paths.
The junior agent must produce a table like this before editing Kotlin:

| Action | Code | Subtype | Exact payload template | Parsed reply | Terminal? |
|---|---:|---:|---|---|---|
| start | | | | | no |
| query state | | | | | no |
| submit point/parameters | | | | | no |
| pause/resume | | | | | no |
| complete | | | | | yes |
| cancel | | | | | yes |

If a row is not applicable, state why. Blank rows mean the contract is not
ready to implement.

## Required tests

At minimum, add or update tests in these areas:

```text
shared/src/commonTest/.../protocol/       constants and frame contracts
shared/src/commonTest/.../domain/         controller/state-machine behaviour
composeApp/src/commonTest/.../ui/         exposure and safety contracts
```

Every testable request must pin:

- numeric opcode;
- subtype;
- exact payload including case and trailing semicolon;
- legal transition from the current state;
- no optimistic success before a parsed acknowledgement/read-back;
- timeout, protocol error, and unexpected reply handling;
- cancel/recovery behaviour for workflows.

Run:

```bash
./gradlew check :androidApp:assembleDebug :desktopApp:createDistributable --console=plain
```

Do not weaken an existing test to make a new implementation pass. If an old
test pins an inferred mapping, replace it with a test citing the derived Benro
contract and explain the correction in the commit.

## Hardware qualification

Hardware testing is validation, not initial contract discovery. Before sending
camera commands:

- camera battery is charged;
- correct USB mode is selected;
- route provenance identifies the Polaris;
- port 22 and port 9090 are open;
- no second client owns the session;
- original camera settings are recorded;
- destructive/media operations have explicit user authorization.

For every tested feature record:

- camera model and firmware;
- OpenPolaris commit, patcher build, and deployed firmware identity;
- exact requests and raw replies;
- timing and all intermediate states;
- resulting JPEG/video/file validity where applicable;
- restoration of the original setting;
- PASS, FAIL, BLOCKED, NOT TESTED, or N/A with a reason.

HTTP 200 or a successful command write is not sufficient evidence. Preview
requires non-empty valid JPEG frames and cadence; capture requires a completed,
decodable output file; a setting requires matching read-back.

## Stop conditions

Stop and report evidence instead of guessing when:

- JADX did not reconstruct the relevant method body;
- a parameter's meaning cannot be established from request method plus call sites;
- request construction and response parser disagree;
- a workflow can create/delete media and authorization is absent;
- the camera becomes unresponsive or a cancel/restore step fails;
- K-3 III and K-1 II behave differently without a capability discriminator.

These are reasons to request a focused live trace or open a narrow issue. They
are not reasons to invent a payload or silently mark a feature unsupported.

## Definition of done for one feature

A feature is done only when:

- its complete request and response contract is documented;
- constants, parser, controller, and UI use the same named contract;
- software gates pass;
- errors and timeouts never appear as success;
- multi-step operations can cancel and recover;
- K-3 III and K-1 II are each classified with evidence;
- original settings are restored;
- issue #63 is updated and any distinct failure has an owning issue.

Issue #62 can close when no exposed Camera control uses the inferred sequential
map or optimistic writes. Issue #63 closes only when every inventory row is
classified for both camera bodies.
