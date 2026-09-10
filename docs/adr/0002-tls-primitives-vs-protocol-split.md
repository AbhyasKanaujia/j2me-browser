# 0002: Split TLS into ported primitives vs. from-scratch protocol logic

## Status

Accepted

## Context

CLDC 1.1 has no `java.math.BigInteger` and no built-in crypto of any kind.
Per [0001](0001-no-proxy-self-sufficient-client.md), TLS must be
implemented entirely in CLDC-1.1-compatible Java (roughly a Java 1.3
language level: no generics, limited collections) and bundled directly in
the MIDlet JAR — there is no platform HTTPS to fall back on.

CLDC 1.1 is a frozen specification with no actively maintained upstream
crypto ecosystem. This matters for how the code should be sourced: unlike
a modern-platform project, there is no living library to defer to later.
Whatever ships here effectively *is* the long-term-maintained version.
"Grab whatever J2ME crypto library solves today's problem" was considered
and rejected as an anti-pattern for that reason — it optimizes for getting
something working now at the cost of leaving behind ported, unmaintained
logic nobody understands well enough to extend to a future TLS version.

## Decision

Split the TLS implementation into two parts with different provenance:

- **Primitives** (AES, SHA-2, RSA/EC big-integer math) — this math is
  settled and doesn't change over time, so it's reasonable to adapt from
  existing prior art. Bouncy Castle's archived J2ME/CLDC "Lightweight
  Crypto API" (`lcrypto-j2me-*.zip`, MIT-style license) is the identified
  source of raw material for this layer, cleanly attributed.
- **Protocol/handshake logic** (record layer, cipher suite negotiation,
  key exchange) — implemented directly against the IETF RFCs (TLS 1.2 =
  [RFC 5246](https://www.rfc-editor.org/rfc/rfc5246), as updated by
  [RFC 7627](https://www.rfc-editor.org/rfc/rfc7627) — Extended Master
  Secret, which mitigates the triple-handshake class of attack and should
  be treated as required, not optional, for any TLS 1.2 implementation
  written today), not ported from any existing implementation. This is
  the part that needs to survive future TLS versions and future
  contributors, so it's grounded in spec documents rather than in someone
  else's abandoned codebase. (RFC 5246 itself is marked obsolete by the
  RFC editor in favor of TLS 1.3/RFC 8446 — that's a statement about which
  version is current, not about 5246 being wrong to implement; TLS 1.3 is
  out of scope for now, see [ADR-0003](0003-ecdhe-p256-aes-gcm-cipher-suite.md).)

## Consequences

- A future contributor with zero J2ME background, but familiarity with the
  TLS RFCs, can pick up the protocol layer without first reverse-engineering
  an old library's design choices.
- The primitives layer carries a real external dependency (Bouncy Castle's
  archived code) with its own attribution and licensing obligations to
  keep straight.
- Two different skill sets are needed to review/extend this module —
  cryptographic-primitive correctness on one side, protocol-spec compliance
  on the other — and the module boundary should stay drawn so those can be
  reviewed independently.
- This split is a general pattern worth repeating elsewhere in the project
  where "the math is settled but the surrounding logic will keep
  evolving" applies — e.g. NetSurf's own parser/DOM/CSS library split
  (its HTML parser, DOM, and CSS engine ship as separate libraries) is an
  existing precedent for the same idea.
