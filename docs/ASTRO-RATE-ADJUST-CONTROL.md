# Astro rate-adjust control

Status: design specification only. The control must remain unavailable until
the target Polaris firmware's independent-rate command, units, acknowledgements,
watchdog and physical stop behaviour are qualified.

This is a separate control from the existing `Sidereal`/`Lunar` selector,
`½ speed` toggle and `AHRS` toggle in `PositionReadout`.

## What the control means

The existing controls have distinct meanings:

| Control | Current wire path | Meaning |
|---|---|---|
| Sidereal / Lunar | 531 `state:1;speed:0|2;` | Select the base tracking profile. |
| ½ speed | 536 `halfSpeed:0|1;` | Firmware global half-speed mode; the payload is inverted. |
| AHRS | 520 `state:0|1;` | Enable or disable quaternion/position reporting. |
| Fine rate | **future, gated** | Adjust one or more physical motor-axis rates while tracking. |

Fine rate must not be implemented as another `531 speed:` value. The observed
531 selector has only the verified sidereal and lunar indices. It also must not
be implemented as repeated 513/514/521 jog calls: those are manual jog paths,
not a qualified continuous tracking-rate primitive.

The intended fine-rate model is:

```text
base tracking profile (sidereal or lunar)
        + bounded per-axis correction
        -> rate controller
        -> verified mount command and state confirmation
```

The axes are physical M1/pan, M2/tilt and M3/rotation. After M3 has rotated,
M1 and M2 must not be described as fixed azimuth and altitude axes.

## Proposed UI

Keep the existing compact controls together in `PositionReadout`:

```text
Track   Sidereal   Lunar   ½ speed   AHRS   Fine rate
```

The current Sidereal/Lunar row may be rearranged into this same control group,
but the labels and meanings must remain explicit. `Fine rate` opens a bounded
rate-adjust sheet rather than sending a motor command on every slider tick.

The sheet should contain:

1. connection and tracking status;
2. the selected base profile;
3. M1/Pan, M2/Tilt and M3/Rotation correction values;
4. the allowed range and units supplied by the firmware capability record;
5. `Apply`, `Cancel` and an explicit `Stop correction` action;
6. a warning when the rate is requested but not confirmed by the mount.

Human-facing values should be shown in arcseconds/second where practical;
the wire/controller layer may use degrees/second. Do not display a range until
the target firmware and physical rate matrix establish it. A hard-coded range
based on another Polaris firmware or the Alpaca project is unsafe.

The control is disabled when any of these is true:

- no connected mount or no current mode/status confirmation;
- tracking is not confirmed active;
- the firmware capability is absent, unknown or unqualified;
- ½ speed is enabled, unless a later qualified contract proves that the two
  modes compose safely;
- the controller has lost its lease, acknowledgement or link.

The disabled state should explain why: `Fine rate unavailable on this firmware`
is preferable to presenting a slider that appears to work but only queues
unknown bytes.

## Controller boundary

Do not put rate serialization in the Compose UI or add it directly to the
existing `TrackingController`. Add a separate domain boundary when the wire
contract is qualified:

```kotlin
interface RateAdjustController {
    val state: StateFlow<RateAdjustState>
    suspend fun apply(request: RateAdjustRequest): RateCommandResult
    suspend fun stopCorrection(): RateCommandResult
    suspend fun onTrackingStopped()
    suspend fun onDisconnected()
}
```

The request should contain a base profile, three signed axis targets, a
monotonic request/version number and the firmware capability identity. The
state should distinguish:

- unavailable;
- available but idle;
- applying;
- active and confirmed;
- stale/lease-expired;
- rejected or faulted.

Every applied request must be bounded, coalesced and ramped. A UI slider must
not generate an unbounded stream of motor commands. `Apply` should supersede
the previous request atomically, and `Stop correction` must have priority over
queued updates.

## Candidate host-side implementation

The patcher research has a host-only native-C planner and scheduler based on
the known SLOW application controls:

| Axis | Candidate control | Payload |
|---|---:|---|
| M1/pan | 532 | `key:<0|1>;state:<1|2>;level:<1..5>;` |
| M2/tilt | 533 | same |
| M3/rotation | 534 | same |

That planner selects one common SLOW level and time-shares adjacent direction
and state rungs to approximate a requested average rate. A held zero-rate axis
uses balanced state-1 directions; state 0 is reserved for an explicit stop.
The host-side scheduler enforces minimum dwell and emits explicit logical STOP
events.

This is a design reference, not an approved OpenPolaris wire implementation.
The exact MCU route, units, axis signs, acknowledgements, watchdog and physical
semantics are still open. The reverse-engineered `AC 4A` float helper is also
not approved for live use. OpenPolaris must therefore implement the abstract
controller first and keep the real transport adapter behind a capability and
feature gate.

Do not silently fall back from an unavailable rate controller to jog commands.
The safe fallback is to leave the selected base tracking profile unchanged and
report that fine correction is unavailable.

## Lifecycle and safety rules

1. On connect, initialise rate control as unavailable until the mount identity
   and capability record are known.
2. Start ordinary tracking through the existing explicit 531 profile path.
3. Allow Fine rate only after tracking-active status is confirmed.
4. Apply only finite, bounded, capability-approved axis targets.
5. Refresh the controller lease from the owner, not from the UI. UI loss must
   stop correction.
6. On stop tracking, Astro-mode exit, disconnect, timeout, process shutdown or
   rejected acknowledgement, stop all rate corrections and clear active state.
7. If a host watchdog expires, reset the scheduler as well as the transport
   state before accepting another request.
8. Keep the existing K-3 III/camera control paths and ordinary Sidereal/Lunar,
   ½ speed and AHRS behaviour unchanged.

Rate correction is not the same as pointing correction. The future Astro
plate-solve loop may calculate a drift correction, but it must not call this
controller until the solve passes its confidence, freshness and rotation gates.

## Verification plan

### Unit and simulator tests

- conversion between UI units and controller units;
- finite values, bounds, acceleration and common-level validation;
- M1/M2/M3 sign and payload mapping;
- minimum dwell and scheduler transitions;
- STOP priority and repeated-STOP rejection;
- malformed payload, unknown axis, stale request and bad acknowledgement;
- watchdog expiry, disconnect and process-exit cleanup;
- ½-speed/custom-rate mutual exclusion;
- no command when the feature flag or capability is disabled.

### Hardware gates

Before enabling the real adapter, test with no camera attached:

- each axis independently in both signs;
- simultaneous three-axis request;
- the lowest stable correction rate;
- immediate STOP and stop latency;
- link loss, application exit and reconnect;
- target firmware identity and positive state confirmation;
- physical drift/rotation response under a known load.

Only after those gates pass should the control be made visible as an enabled
feature. Until then, simulator support may exercise the UI and state machine,
but the production adapter must remain unavailable.

## Implementation order for OpenPolaris

1. Keep the current Sidereal/Lunar, ½ speed and AHRS controls unchanged.
2. Add a disabled `Fine rate` presentation and an explanatory state only after
   the domain model and feature flag exist.
3. Add the abstract `RateAdjustController` and simulator implementation.
4. Add unit/state-machine tests and UI tests for disabled, applying, active,
   stale and faulted states.
5. Add the target-firmware capability record and transport adapter only after
   the firmware evidence gates are complete.
6. Enable the control on a tested firmware allow-list, never by merely seeing
   a familiar app or firmware version string.

The current implementation should stop after step 4. The UI can then be
reviewed without creating an accidental live motor-control path.
