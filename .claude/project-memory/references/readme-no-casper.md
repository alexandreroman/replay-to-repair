---
name: "README must not mention Casper"
description: "Keep Casper out of the public README; it may stay in CLAUDE.md"
type: feedback
---

# README must not mention Casper

The public `README.md` must NOT mention Casper (the internal
worktree/workspace tool) — no Casper section, no `casper run`
command table, no references to `CASPER_PORT`. Casper-specific
details (the `make worktree-init` port remap, per-worktree port
offsets) may live in `CLAUDE.md` but stay out of `README.md`.

**Why:** the README targets a public/conference audience; Casper
is an internal developer tool that would be noise (and confusing)
to external readers.

**How to apply:** when editing or generating `README.md`, omit any
Casper content. When documenting the port scheme, describe the
plain defaults (8080 gateway / 7233 Temporal gRPC / 8081 dev
backend) without the Casper remap. Casper port-remap docs go in
`CLAUDE.md` only.
