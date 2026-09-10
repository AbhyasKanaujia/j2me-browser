# 0004: Target the constrained end of the hardware range as the real floor

## Status

Accepted (superseded an earlier, less concrete version of this decision —
see History below)

## Context

CLDC 1.1 / MIDP 2.0 spans a wide hardware range — from early-to-mid Series
40 feature phones (~200-300MHz ARM9-class CPUs, mid-2000s) up through
later Asha-platform-era devices (~1GHz ARM11-class). A project targeting
"CLDC 1.1" as a spec could reasonably choose to develop against the
comfortable, high-end tier and treat the low end as best-effort, or the
reverse.

Development and testing happen against real owned hardware at both ends
of that range, not hypothetical devices — this matters practically, not
just rhetorically: real-device testing has already catches bugs that
emulator-only testing may miss.

## Decision

Target the **constrained, low-end tier as the actual engineering floor**,
not the comfortable/high-end tier. The higher-end hardware is expected to
inherit compatibility for free by building for the harder constraint,
rather than maintaining a separate "lite" build or special-casing the
low-end tier.

If a feature doesn't run acceptably on the low-end floor device, that is
treated as a real problem to fix in the implementation — not something
solved by quietly raising the effective baseline.

## Consequences

- One codebase serves the full target range by construction; no separate
  build variant or feature-flag split by device tier.
- Every resource budget in the project (JAR size for OTA install, heap
  usage, TLS handshake latency — see
  [0003](0003-ecdhe-p256-aes-gcm-cipher-suite.md)) must be validated
  against the low-end floor device, not just the comfortable tier, or the
  numbers are meaningless.
- Reaches a larger and more iconic slice of the real S40 install base
  (and its enthusiast/contributor community) than optimizing for
  premium-tier hardware only would.
- Concrete JAR-size and heap figures for the floor device are, as of this
  writing, unverified — treated as genuinely unknown pending real
  measurement on physical hardware once there is compiled code to measure,
  rather than backfilled with an inferred number.

## History

This decision went through two earlier, less settled forms before landing
here: an initial generic description of the low-end tier by a specific
phone model the maintainer didn't actually own, then a correction to use
owned hardware as the real grounding for every number in this decision.
The specific device models used for validation are tracked outside the
public docs (session memory) rather than in this ADR or the README, which
describe the baseline only by hardware category — the category is what's
architecturally load-bearing; the specific model is validation detail that
can change if the hardware on hand changes.
