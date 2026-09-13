# 0006: One file per concern, flat default package

## Status

Accepted

## Context

The codebase grew organically as features landed — HTTP fetch, redirects,
cookies, go-to-address navigation, back/forward history, HTML
tag-stripping/entity-decoding, block-level structure, and Canvas
rendering/scroll/touch input — all inside one file, `src/MainMIDlet.java`,
which reached 718 lines mixing at least four genuinely separate concerns:
MIDlet lifecycle/command wiring/navigation history, HTTP transport,
HTML-to-text parsing, and Canvas rendering.

`AGENTS.md` already named the intended direction ("split them into one
small file per concern... see `tunemeta-midlet`'s `src/` for the pattern"),
but that direction had not yet been formalized as a decision or executed.

Five real-world reference projects were researched — ranging from a small
sibling hobby project to Sun's own mature toolkit — to check whether "one
file per concern" actually holds up at different scales, rather than
assuming it from theory alone:

- **`tunemeta-midlet`** (sibling project, same author/toolchain): flat
  default package, 5 small single-purpose files (`Http.java`,
  `TextWrap.java`, `MiniJson.java`, `Track.java`, `MainMIDlet.java`).
- **`discord-j2me`** (actively maintained MIDP2/CLDC J2ME app, ~18k lines,
  v3.0.0 as of mid-2024): one class per file, single package, ~107 files —
  screens, dialogs, data models, network, and text formatting each
  separated; most files stay under 200 lines even at this scale.
- **`lwuit-for-series-40`** (Sun's own mature LWUIT toolkit, ported to this
  project's exact target platform): sub-packaged by concern; its `html/`
  package — the closest analog to this project's own domain — splits
  parsing (`HTMLParser`), styling (`CSSParser`/`CSSEngine`/`CSSElement`),
  the DOM node (`HTMLElement`), networking
  (`DocumentRequestHandler`/`ResourceThreadQueue`), and feature-specific
  pieces (`HTMLForm`/`HTMLTable`/`HTMLLink`) into 29 separate files.
- **Dillo** (C++): `html.cc`/`css.cc` separate from `cache`/`cookies`/
  `auth`/`dns` (networking) separate from `dw/` (rendering widgets, split
  further by widget type) separate from `bookmark`/`history`/`menu` (app
  chrome).
- **Lynx** (C): `WWW/Library` (protocol layer) separate from `src/`, where
  cookies, bookmarks, history, downloads, and forms each get their own
  file.

The pattern holds at every scale examined: one file per cohesive concern,
with boundaries tracking the fetch → parse → style → render → chrome
pipeline. File count grows with project size; the unit of decomposition
does not change.

This also matches established software design guidance. Robert C.
Martin's Single Responsibility Principle ("a class or module should have
one, and only one, reason to change" — *Clean Code*) names exactly the
problem with the current file, which has at least four independent
reasons to change. Martin Fowler's "Large Class" code smell and its
remedy, "Extract Class" (*Refactoring: Improving the Design of Existing
Code*), describe exactly the mechanical fix being applied here. Angela
Yu's *12 Rules to Learn to Code* names the same idea from a learnability
angle — Rule 11, "Get into the Habit of Chunking": breaking work into
small, independently-graspable pieces. That angle matters concretely here
since this project has no automated test suite (`AGENTS.md`) and depends
on a change being small enough to reason about and manually verify in
MicroEmulator or on real hardware.

## Decision

Split `src/MainMIDlet.java` into one file per concern, along boundaries
the codebase already has, mapping directly onto the roadmap's own layers:

- `MainMIDlet.java` — MIDlet lifecycle, command wiring, navigation
  (back/forward) stack. Orchestration only; delegates to the files below.
- `Http.java` — fetch, redirects, cookies, URL resolution (Transport
  layer).
- `HtmlText.java` — tag stripping, entity decoding, block-level structure
  (Parsing layer, current stage).
- `BrowserCanvas.java` — rendering, word-wrap, key and touch scrolling
  (Rendering/UI layer).

Stay in the default package (no `package` declarations), matching
`tunemeta-midlet`'s already-adopted convention for this exact toolchain.
Sub-packages (as LWUIT eventually needed, at roughly 30x this project's
current file count) are not warranted at this project's current or
near-future scale.

New concerns get new files as they land (a future real HTML parser tree,
a CSS engine, an app-shell History/Bookmarks module) rather than being
folded into an existing file that already has a distinct responsibility.

## Consequences

- Each file stays reasoned-about-able in isolation, and a change to (say)
  cookie handling can't accidentally touch rendering code in the same
  diff.
- `AGENTS.md`'s "Layout" note can be marked resolved rather than
  aspirational once this lands.
- No automated test suite exists, so keeping files small and single-
  purpose is doing double duty here: it's also what keeps manual
  verification (MicroEmulator/real device, per `AGENTS.md`) tractable,
  since a smaller, focused diff has fewer places a regression could hide.
- Future contributors adding a new roadmap-layer feature (CSS parser,
  forms, history/bookmarks via `RecordStore`) should default to a new
  file, not extending one of these four — consistent with how
  `discord-j2me` and LWUIT keep growing by adding files rather than
  growing existing ones indefinitely.
- This is a mechanical reorganization, not a behavior change — no
  functional difference is expected; verification is the same manual
  MicroEmulator/device check used for every other change in this project.
