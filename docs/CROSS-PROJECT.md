# Cross-project coordination

> **Status: convention, not a contract.** This file describes how the
> OpenPolaris protocol-RE work and the
> [`benro-polaris-firmware-patcher`](https://github.com/ian-morgan99/benro-polaris-firmware-patcher)
> tool stay in sync. Update it when the convention changes.

## Related project

| | |
|---|---|
| Repo | [`ian-morgan99/benro-polaris-firmware-patcher`](https://github.com/ian-morgan99/benro-polaris-firmware-patcher) |
| Upstream of patcher | [`blaineam/benro-polaris-firmware-patcher`](https://github.com/blaineam/benro-polaris-firmware-patcher) — keep personal RE findings in the fork |
| What lives there | Patch scripts (`patch-polaris.sh` / `.ps1`), `FwPkt` build pipeline, post-mortems, UBIFS repackaging |
| What lives here | Protocol codes, wire formats, parser, HW test results, RE notes |

**Rule:** if a fact appears in both repos, one is stale. Reference by permalink; do not copy.

## How to flag a patcher-relevant finding

When a protocol RE result might change how the patcher behaves, open an issue in **this** repo and:

1. Apply the `affects-patcher` label.
2. In the body, reference the patcher issue using the form
   `ian-morgan99/benro-polaris-firmware-patcher#NN` (GitHub will auto-create
   the backlink on the patcher side).

Typical triggers: new HW-verified code, new wire-format quirk, brick-risk
boundary change, parser-breaking structural difference.

## How to read what the patcher needs

- Open patcher issues tagged `needs-protocol-re`:
  [label filter](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/issues?q=is%3Aissue+is%3Aopen+label%3Aneeds-protocol-re)
- Mirror of this file on the patcher side:
  [`benro-polaris-firmware-patcher/docs/CROSS-PROJECT.md`](https://github.com/ian-morgan99/benro-polaris-firmware-patcher/blob/main/docs/CROSS-PROJECT.md)

## Source-of-truth files (this repo)

| Concern | Path |
|---|---|
| Code catalogue | [`docs/PROTOCOL.md`](PROTOCOL.md), supplemented by `/tmp/polaris-functions-report.md` during active RE |
| Static firmware analysis | [`docs/FIRMWARE-ANALYSIS-ALPACA.md`](FIRMWARE-ANALYSIS-ALPACA.md) |
| Live evidence (gimbal SSH / 9090 captures) | [`docs/evidence/`](evidence/) |
| Parser (Kotlin) | `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/` |

Pin the patcher to **commit SHAs**, not branches, when citing these files in
patcher-side issues — main moves.
