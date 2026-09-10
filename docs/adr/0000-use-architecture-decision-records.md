# 0000: Use Architecture Decision Records

## Status

Accepted

## Context

This project is a from-scratch browser for a platform (J2ME/CLDC 1.1)
most contributors won't have touched before, and several foundational,
hard-to-reverse decisions had already been made — no-proxy design, TLS
primitives/protocol split, cipher suite choice, device baseline — before
any permanent record of the *why* existed. That reasoning was sitting only
in README prose, with no dated, stable place a future contributor could point 
back to.

A full ARCHITECTURE.md-style codemap was considered instead. General
guidance on that format (see matklad's widely-cited
[ARCHITECTURE.md](https://matklad.github.io/2021/02/06/ARCHITECTURE.md.html))
suggests it pays off once a project reaches roughly 10k–200k lines of
code, with modules that actually need mapping. This project is currently
one file, so a codemap would have nothing real to map yet — it would
mostly restate the roadmap already in the README.

## Decision

Record architecture decisions as individual, numbered files in
`docs/adr/`, following the standard ADR pattern (Michael Nygard's
original proposal):

- One decision per file, numbered sequentially (`0000`, `0001`, ...).
- Standard shape: Status, Context, Decision, Consequences.
- Immutable once accepted — a changed decision gets a new ADR that
  supersedes the old one, rather than an edit to history.
- README keeps short summaries with links into `docs/adr/` for full
  rationale, rather than duplicating it inline.

A full ARCHITECTURE.md/codemap is deliberately deferred until the
transport/parsing/rendering split in the roadmap exists as real, separate
modules — this ADR is the record of why that file doesn't exist yet.

## Consequences

- Every future hard-to-reverse decision gets its own dated ADR instead of
  living only in README prose.
- The README stays shorter and doesn't need editing every time the
  reasoning behind a past decision is revisited — only the relevant ADR
  does, via a new superseding entry.
- No architecture codemap exists yet; that gap should be revisited once
  the codebase actually has multiple modules to map, not before.
