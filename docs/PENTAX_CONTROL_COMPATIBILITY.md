# Pentax control compatibility contract

Revision: 2026-09-08

OpenPolaris must not assume that all cameras expose identical libgphoto2 widget names or that every Pentax body follows the same transport/protocol family.

The normative control audit is maintained in `ian-morgan99/libgphoto2/docs/pentax/GENERIC_CONTROL_COMPATIBILITY_AUDIT.md`. The firmware integration notes are in `ian-morgan99/benro-polaris-firmware-patcher/docs/PENTAX_GENERIC_CONTROL_COMPATIBILITY.md`.

## Client rules

1. Prefer the standard generic libgphoto2 control vocabulary when available (`iso`, `shutterspeed`, `aperture`, `imageformat`, `imagequality`, `autofocus`, `autofocusdrive`, `manualfocusdrive`, `capturetarget`).
2. Capability discovery must be runtime-driven. A missing widget is not equivalent to a camera failure and must not blank unrelated controls.
3. Keep controls independent: `autofocus` policy, immediate `autofocusdrive`, manual lens drive, AF/MF mode selection, image format, card/slot writing mode and capture destination are distinct concepts.
4. Do not infer K-1 II capability from K-3 III. The focus protocol families differ (`0x9016` old-family versus `0x9017` new-family) and K-1 II omits some operations from DeviceInfo that are nonetheless hardware-supported.
5. Treat K-01 as two separate transport identities. The legacy USB-SCSI path and the generic MTP/PTP path do not expose equivalent capability surfaces.
6. Pentax-specific widget names may be used as a compatibility fallback only where their semantics are known to match the generic operation. They must not be guessed from similar names.
7. UI should distinguish unsupported/unavailable from operation failed. One unavailable control must not disable the entire camera-settings surface.

## Regression matrix

When lower-layer fixes land, test OpenPolaris against at least K-3 III and K-1 II separately for control discovery, read/write, focus, preview, capture and reconnect. K-01 must be recorded separately by USB mode/path. Additional Pentax/Ricoh bodies should inherit no support claim until the libgphoto2 model matrix classifies them as hardware-verified or explicitly inferred.
