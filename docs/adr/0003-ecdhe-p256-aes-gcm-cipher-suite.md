# 0003: One fixed cipher suite — ECDHE P-256 + AES-GCM — no negotiator

## Status

Accepted

## Context

Given the decision to hand-write TLS (see
[0002](0002-tls-primitives-vs-protocol-split.md)), the next question is how
much of the TLS 1.2 cipher-suite negotiation surface to actually implement.
A production-grade TLS stack supports many suites and falls back
gracefully; that flexibility exists to interoperate with servers with
different capabilities and to migrate as suites are deprecated.

Constraints specific to this project's hardware baseline (see
[0004](0004-device-baseline-constrained-floor.md)) argue against that:

- OTA install JAR size is constrained on the low end of the target
  hardware range — this favors shipping one well-chosen cipher-suite
  implementation over a flexible multi-suite negotiator, which would cost
  meaningfully more code for suites that would rarely or never be selected
  in practice.
- Heap is similarly constrained on the low end.
- A sourced benchmark (174-bit ECC scalar multiplication, ~400ms on a
  104MHz ARM7, IACR ePrint 2011/712) extrapolates to a P-256-class
  operation landing somewhere in the tens-of-milliseconds-to-low-seconds
  range across this project's hardware range — acceptable UX either way.
  RSA-2048 key exchange, by contrast, was described in research as "beyond
  resource capabilities" on J2ME-class phones in at least one source
  (snippet-level, not independently verified), with RSA-1024 estimated at
  "a few seconds."

## Decision

Target exactly one TLS 1.2 cipher suite: **ECDHE (P-256/secp256r1) key
exchange + AES-GCM + SHA-256.** RSA key exchange is skipped entirely, not
just deprioritized — elliptic-curve math is both cheaper on constrained
CPUs and lighter on constrained heaps than RSA-2048 at equivalent security,
and supporting it would add a second, likely-unused code path.

This is not a flexible negotiator with one suite currently implemented; it
is a deliberate decision not to build negotiation infrastructure at all
for the foreseeable scope of the project.

## Consequences

- Servers that don't offer `TLS_ECDHE_*_WITH_AES_*_GCM_SHA256` will fail
  to connect over HTTPS with this browser. This is an accepted, explicit
  tradeoff, not an oversight — in practice ECDHE+AES-GCM is broadly
  supported by servers that support TLS 1.2 at all.
- No RSA big-integer modular exponentiation code path needs to be written,
  tested, or kept correct — meaningfully less surface area in a component
  that is already the highest-risk part of the project.
- If a future contributor wants broader server compatibility, that request
  should be weighed against the JAR-size and heap constraints that
  motivated this decision in the first place, not treated as an obviously
  free addition.
- TLS 1.3 is a documented future direction (RFC 8446) but out of the
  current decision's scope; if pursued later, it should get its own ADR
  rather than expanding this one.
