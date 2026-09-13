# 0005: Staged text-flow rendering, not upfront box-model layout

## Status

Accepted

## Context

The rendering bar this project targets (see README) is deliberately modest
— roughly a 2011 Android Gingerbread stock browser, with a narrow CSS
subset (font-size, alignment, margins) planned as its own later roadmap
item, not built yet.

Real browsers used as reference points elsewhere in this project — Dillo
and NetSurf — both do genuine CSS box-model layout: block elements get
actual boxes with computed margins/padding, headings render at distinct
font sizes/weights as part of that same layout pass, and lists get real
indentation with bullet/number glyphs via `list-style`. Building that
requires a CSS parser and cascade to exist first, plus a layout engine
that computes box dimensions from it — substantially more upfront work
than justified before the basic parse-to-readable-text pipeline was even
proven out.

The roadmap instead splits HTML/rendering into stages: (1) strip tags to
plain readable text, (2) recognize block-level structure for layout, (3)
inline formatting (bold/italic/size), (4) links for hit-testing — each
independently shippable, deferring CSS-driven visual layout until the CSS
subset itself exists to drive it.

## Decision

For stage 2 (block-level structure), approximate paragraph/heading/list
boundaries with forced line breaks and a plain `"- "` list-item prefix,
inserted directly during tag-stripping — not real block boxes with margins
or indentation. This is closer to how a text-mode tool like `lynx -dump`
flattens HTML into readable text than to Dillo/NetSurf's actual box-model
rendering.

Concretely: block-level tags (`p`, `div`, `h1`–`h6`, `li`, `tr`, `table`,
`ul`, `ol`, `blockquote`, `hr`) queue a forced line break instead of the
plain space used for inline tags, so blocks land on their own line instead
of running together. `<li>` also queues a `"- "` prefix, with no
distinction between ordered and unordered lists and no nesting-aware
indentation.

## Consequences

- A meaningfully more readable page view ships now, without waiting on the
  CSS parser to exist.
- Headings look identical to normal paragraph text (no size/weight
  distinction) until stage 3 and the later CSS subset land — an accepted,
  temporary limitation, not an oversight.
- All list items get the same `"- "` prefix regardless of list type or
  nesting depth — a known simplification.
- When real CSS-driven layout is eventually built, this line-break-based
  approximation is expected to be replaced outright, not incrementally
  extended toward genuine box-model layout — a future contributor
  shouldn't invest in growing it further in that direction.
