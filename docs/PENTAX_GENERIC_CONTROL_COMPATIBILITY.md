# Pentax generic-control compatibility

Revision: 2026-09-08

OpenPolaris should treat camera capabilities according to the libgphoto2 controls actually exposed by the Polaris backend, while remaining tolerant of controls that are genuinely unavailable for a given Pentax/Ricoh model or USB mode.

Primary protocol/API ownership is `ian-morgan99/libgphoto2`:

- #51 — discovery/index thread
- #52 — full Pentax/Ricoh model × control sweep
- child issues track each control independently

## Client rules

- Prefer generic controls (`aperture`, `imageformat`, `imagequality`, focus controls, etc.) when the backend exposes them correctly.
- Do not assume every Pentax body shares the K-3 III capability surface.
- Treat K-01 USB-SCSI and K-01 PTP/MTP as distinct capability profiles.
- A missing control should degrade gracefully in UI rather than imply the camera failed to connect.
- Do not infer `capturetarget` semantics from card/slot controls.
- Keep capability discovery separate from action success: a widget may exist but still reject a model/state-specific SET/action.

Once libgphoto2 child issues land, OpenPolaris should validate that its UI enables only controls confirmed by capability discovery and does not hard-code Canon/Nikon assumptions that recreate the same mismatch at the app layer.