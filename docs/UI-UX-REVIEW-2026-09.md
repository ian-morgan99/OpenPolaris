# OpenPolaris UI/UX Review — 2026-09

User-driven review request:
> Can you now review the entire user interface. can every control be seen on
> screen, regardless of screen size or layout? Are the screen layouts sensible?
> Do they follow good GUI practice? Do we have separate sections for admin and
> config, vs day to day operation? Make it the best of breed.

This document is the audit; concrete fixes land in the same release
(`v0.1.5`) as the changes they reference.

## Scope and method

UI surface audited (`composeApp/src/commonMain`, `androidApp/src/androidMain`,
`composeApp/src/jvmMain` — 6,830 LoC across 14 files):

| File | LoC | Role |
| --- | ---: | --- |
| `ui/AppViewModel.kt` | 2343 | All VM state + command handlers |
| `androidVRActivity.kt` (VRActivity) | 956 | Android stereoscopic VR preview |
| `ui/Panes.kt` | 810 | Connection, Password, Goto, Camera, Preview, Helpers, Firmware panes |
| `androidMainActivity.kt` | 356 | Android host: Wi-Fi/BT/VR wiring |
| `ui/TonightPane.kt` | 330 | Tonight astro helpers (PC98 / catalog) |
| `androidMountWifiScan.kt` | 317 | BT-wake + Wi-Fi scan for "Find & wake Polaris" |
| `ui/FullControlPanes.kt` | 478 | Astro helpers, dither, settling, limits, auto-level |
| `ui/OpenPolarisApp.kt` | 288 | Root surface, callout rail, status/position/jog layout |
| `ui/FeatureFlagsPane.kt` | 295 | Runtime settings (feature-flag toggles) |
| `ui/ReconnectDialog.kt` | 176 | "Reconnect to <host>?" |
| `ui/ReadmePane.kt` | 100 | In-app quick-start guide |
| `ui/SimulatedMount.kt` | 197 | Demo mode mount |
| `ui/ReconnectPrompt.kt` | 58 | Prompt glue |
| `ui/Theme.kt` | 36 | Material 3 theme |

Findings are grouped into **Layout & responsiveness**, **Discoverability**,
**Admin vs day-to-day**, **Consistency**, **Dead code**, **Dialog system**, and
**Per-pane notes**.

---

## 1. Layout & responsiveness

### 1.1 Binary wide/compact — no Medium

`OpenPolarisApp.kt:105` switches on `widthSizeClass != Compact` (binary). A
7–9" tablet in portrait sits in `Medium` and is treated as "compact": the
horizontal `CalloutRail` lands at the bottom of a tall column and the
side-by-side `JogPane` collapses. This is OK but not great.

**Recommendation:** treat `Expanded` as wide, `Medium` + `Compact` as compact
*but* let the callout rail lay out vertically in Medium so the callouts don't
cram the bottom of a tall pane.

### 1.2 Status + position occupy the top always

`StatusStrip` and `PositionReadout` are always visible. On a 4" 320×568 dp
phone, they take ~140 dp; the jog pad fits in the remaining ~410 dp. On
5" landscape (568×320 dp, the locked orientation), StatusStrip+PositionReadout
together consume nearly half the height before the jog pad has any room. The
Astro/Track filter chips on PositionReadout (Track / ½ speed / AHRS) force
the readout card to a `headlineSmall` Az/Alt label and a row of three chips —
about 110 dp total.

**Recommendation:** in `landscape+Compact` (i.e. the locked phone orientation),
collapse PositionReadout to a single line (`Az --- Alt ---  TRK  ½×  AHRS`)
with the chips as small icon buttons. StatusStrip stays a single line.

### 1.3 Jog pad width is hard-coded

`JogPane` is given `width(260.dp)` in wide mode and `width(220.dp)` in
compact. On a 5" phone landscape the available width for the jog pad (after
the side callout rail) is roughly 320 dp — the 220 dp pad sits centered with
~50 dp of padding on each side, which is fine, but there's no logic that
scales the pad for very small or very large screens. On a desktop window
the same 260 dp pad looks tiny.

**Recommendation:** compute jog pad width as a fraction of available width
(cap 320 dp, floor 200 dp).

---

## 2. Discoverability

### 2.1 Cryptic callout glyphs

`Callout` (OpenPolarisApp.kt:177-187) currently uses:

| Enum | Glyph | What it opens |
| --- | --- | --- |
| Connection | `Wi-Fi` | ConnectionPane |
| Slew | `Slew` | GotoPane (slew + align) |
| Camera | `Cam` | CameraPane |
| Preview | `Preview` | PreviewPane |
| Helpers | `Helpers` | HelpersPane (astro helpers) |
| Firmware | `FW` | FirmwarePane |
| VR | `VR` | (no-op; relies on `onLaunchVr`) |
| Readme | `?` | ReadmePane (in-app guide) |
| Settings | `Cfg` | FeatureFlagsPaneContent |

`"?"` and `"Cfg"` are opaque. `"Cam"`, `"FW"`, `"VR"` are abbreviated to the
point of being unfindable for a new user. There are no tooltips, no icons,
and the buttons themselves are just `TextButton` glyphs.

**Recommendation:** rename the glyphs to their full words: `Wi-Fi`,
`Slew`, `Camera`, `Preview`, `Helpers`, `Firmware`, `3D view`, `Guide`,
`Settings`. The phone has 320 dp to spare in landscape and ~9 short words
fit easily.

### 2.2 "Enable in config" instructions point nowhere

When the user tries something flag-disabled, the VM emits
`"Firmware upload is disabled. Open the 'Settings' callout and turn…"`
(OpenPolarisApp.kt / AppViewModel.kt:1918-2104). The phrase *Settings* is
helpful but the user has to find the callout — and the callout is labelled
`Cfg`. There is no deep-link from the status message into the settings pane.

**Recommendation:** keep the message but also offer an inline "Open Settings"
action that pops the Settings dialog.

### 2.3 The 9-button callout rail is a wall of acronyms

Even after the rename in §2.1, 9 buttons in a horizontal row on a 320 dp
phone is cramped. Buttons are 36 dp tall by default `TextButton` sizing
and the row uses `Arrangement.SpaceEvenly`. With short labels this is fine;
with long labels (e.g. "Firmware", "Settings") it overflows.

**Recommendation:** cap each label to ≤ 8 chars or use icons for the four
most-used (Wi-Fi, Slew, Camera, Preview) and group the rest under a single
"More" overflow button.

---

## 3. Admin vs day-to-day

### 3.1 FeatureFlagsPane mixes safe, unsafe, and destructive flags

The `flagSpecs` table (FeatureFlagsPane.kt:73-145) declares 25 flags with
three categories: `safe` (user-toggleable), `destructive` (explicit
confirm), and neither (read-only display). The current UI renders all 25
flags in a single vertical list, with destructive ones wearing a red
`DESTRUCTIVE` badge. Users have to read each description to know which
flags are safe to flip.

**Recommendation:** add a category header row at the top of the list with
three sub-sections — **Day-to-day** (safe flags), **Advanced**
(non-safe, non-destructive — read-only), and **Admin** (destructive). The
**Admin** section is collapsed by default and has its own header.

### 3.2 No "Reset all" or "Save as default"

`FlagsHeader` has a "Reset all" button (FeatureFlagsPane.kt:189-196) that
restores the in-memory defaults but there is no "Save as default" — every
restart reverts to whatever is baked into the binary. The session store
already knows how to persist strings; persisting the chosen flag set
across restarts is one follow-up.

**Recommendation:** the "Reset all" button is enough for now; document the
limitation in the ReadmePane.

### 3.3 Camera is labelled "experimental"

`CameraPane` (Panes.kt:460) has an "Experimental" banner. This is honest
but it could be more specific — the user can still try it in Demo mode
without reading the doc. Status quo is acceptable.

---

## 4. Consistency

### 4.1 Some dialogs scroll, others don't

`CalloutDialog` (OpenPolarisApp.kt:276-287) wraps every callout in a
non-scrolling `Column` — explicit design choice per the Benro aesthetic.
`FeatureFlagsPaneContent` (FeatureFlagsPane.kt:250-281) wraps its flag list
in `Modifier.verticalScroll(rememberScrollState())` — so the Settings
dialog scrolls while the Firmware dialog clips on small screens.

**Recommendation:** bring them in line. Either:

* (A) Make all dialogs scroll — consistent, no clipping. The Benro
  aesthetic is satisfied as long as the scroll bar is hidden / subtle.
* (B) Make Settings non-scrolling too — but it has 25 rows, so it would
  clip on a 5" phone.

Going with (A). Wrap the body in `Modifier.verticalScroll(rememberScrollState())`
inside `CalloutDialog`.

### 4.2 Card wrapper inconsistency

`FeatureFlagsPane` wraps its content in a `Card`; the dialog body is
expected to be plain. But every other pane (`ConnectionPane`, `GotoPane`,
`CameraPane`, `PreviewPane`, `HelpersPane`, `FirmwarePane`, `ReadmePane`)
also wraps itself in a `Card` *and* lives inside a dialog body that doesn't
add its own card. Result: every dialog shows a card-in-card.

**Recommendation:** drop the per-pane `Card` wrapper when the pane is
hosted in a `CalloutDialog`; keep it for free-standing embeds (like the
VR activity).

---

## 5. Dead code

### 5.1 `StatusPane` is unreachable

`StatusPane` (Panes.kt:273-294) is defined but no caller references it
(grepped the whole tree). It duplicates the always-visible `StatusStrip` +
`PositionReadout` and was probably written as a candidate replacement that
was never adopted.

**Recommendation:** delete.

### 5.2 `JogPane` IS reachable — and it IS the always-visible one

`JogPane` (Panes.kt:298-308) is the function called from
`OpenPolarisApp.kt:118` and `:128`. The `JogPad` it composes is the
central control on the main surface. No dead code here, despite the
naming overlap with `JogButton` and `jogPadSteps`.

### 5.3 `Callout.VR -> { dialog = null }` is a no-op stub

`Callout.VR` (OpenPolarisApp.kt:150) just clears the dialog — the actual
launch is in the `CalloutRail.handle` lambda (line 199) which calls
`onLaunchVr?.invoke()`. The two-stage dispatch is correct (avoids a
flicker between dialog close and activity start) but it isn't obvious from
reading the code.

**Recommendation:** leave the behaviour, add a comment explaining the
intentional two-stage.

---

## 6. Per-pane notes

### 6.1 ConnectionPane (Panes.kt:73-183)

7 buttons: Connect, Wake, Demo, Disconnect, Bridge, Find & wake Polaris,
Open system Wi-Fi. On a 5" landscape dialog the row wraps to two lines,
which is fine. A `LazyVerticalGrid` would be tidier.

### 6.2 GotoPane / Slew & align (Panes.kt:327-451)

The most complex pane — RA/Dec *or* Az/Alt input, plate solve, star
alignment, auto-level. In landscape with a 320 dp dialog, the
coordinate-input row already overflows onto a second line. Acceptable.

### 6.3 CameraPane (Panes.kt:460-491)

10 steppers in a single column. Already scrolls. Add a category header
"Shutter" / "Aperture" / "ISO" once more camera capabilities land.

### 6.4 PreviewPane (Panes.kt:509-549)

Renders a placeholder box when no frame is available. Fine.

### 6.5 HelpersPane (FullControlPanes.kt:43-62)

Dither, Settling, Limits, AutoLevel. Each row has a label + value + switch
or input + Refresh. Consistent.

### 6.6 FirmwarePane (Panes.kt:601-799)

File picker, expected MD5, delivery mode, SSH host, progress. Longest
pane — must scroll.

### 6.7 ReadmePane (ReadmePane.kt:20-100)

Six numbered steps + Safety + Camera + a "full docs" footer. Tidy. Could
add deep-links into the relevant callouts ("Step 1: tap **Wi-Fi**" → opens
the Connection dialog). Not in scope for v0.1.5.

---

## 7. Fixes landed in v0.1.5

| Fix | File | Effect |
| --- | --- | --- |
| Glyphs → full-word labels | `OpenPolarisApp.kt` | `Cam → Camera`, `FW → Firmware`, `VR → 3D view`, `? → Guide`, `Cfg → Settings` |
| All dialogs scrollable | `OpenPolarisApp.kt` | Wraps `CalloutDialog` body in `Modifier.verticalScroll` |
| Settings split into Day-to-day / Advanced / Admin | `FeatureFlagsPane.kt` | New `FlagsSection` header; destructive flags grouped under "Admin" |
| Dead-code removal | `Panes.kt` | `StatusPane` deleted |
| VR two-stage comment | `OpenPolarisApp.kt` | Inline comment explaining the intentional no-op + activity launch |
