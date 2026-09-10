# 0001: No-proxy, fully self-sufficient client

## Status

Accepted

## Context

Most historical "mobile browsers" for feature phones — Opera Mini, UC
Browser — worked by proxying every request through a company-run server
that did the real fetching, JS execution, transcoding, and TLS termination,
then sent the phone a compressed result. This is the obvious way to make a
capable-feeling browser on very limited hardware, since it moves the hard
parts (TLS, HTML/JS, rendering-heavy pages) off the device entirely.

That model has two failure modes this project needs to avoid:

1. It cannot reach LAN/localhost addresses, since every request is routed
   through a third party's server first. Reaching local network addresses
   is a hard requirement for this project.
2. The browser's survival becomes dependent on someone else's server
   staying up indefinitely. UC Browser's J2ME client stopped working
   outright when its proxy was shut down. Opera Mini's J2ME-era client only
   still works because Opera continues to pay to run the proxy — a
   continuation decision this project has no control over and shouldn't
   depend on.

## Decision

Everything the browser does — HTTP(S) transport, TLS, HTML/CSS parsing,
and rendering — runs on-device. No server-side component is required for
the browser to function, ever.

The direct consequence is that TLS must be implemented from scratch for
CLDC 1.1 (see [0002](0002-tls-primitives-vs-protocol-split.md)), since
there is no proxy to offload it to and no standard-library HTTPS to fall
back on.

The rendering bar is set accordingly modest — intentionally matched to
~2011 Android Gingerbread's stock browser rather than a modern engine.
JavaScript, frames/iframes, video/audio playback, and modern CSS
(flexbox, grid, animations, media queries) are permanently out of scope,
not deferred. Pages that depend on these render degraded or blank, the
same way they did on real feature-phone browsers of that era — this is
expected behavior, not a bug to chase.

## Consequences

- No dependency on any company or server continuing to exist for the
  browser to keep working — the project's lifespan is bounded only by
  whether someone maintains the code, not by a third party's hosting
  decision.
- Full access to LAN/localhost, which a proxy architecture would make
  impossible.
- Significantly more implementation work up front (a from-scratch TLS
  stack, HTML parser, and layout engine on a constrained runtime) in
  exchange for not inheriting the proxy model's failure modes.
- A firm, permanent scope ceiling (no JS, no frames, no modern CSS) is
  required to keep that implementation work tractable — see the README's
  "How much of a browser is this?" section for the current scope line.
