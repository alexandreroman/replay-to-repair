---
name: "Demo design constraints"
description: "Bug lives in the Activity, Workflow stays linear, no-owner fails it non-retryably, neutral owner vocabulary"
type: project
---

# Demo design constraints

The demonstrated bug always lives in the Activity, never in the Workflow. The
Workflow code stays linear: it holds no conditional branching or early return
and delegates every decision to Activities. It completes normally on the happy
path, and a non-retryable Activity failure terminates it in error.

The `selectOwner` Activity assigns an owner from the roster. When the LLM finds
no suitable owner it replies with the exact token `none`, which the Activity
turns into a non-retryable `ApplicationFailure` of type `NoSuitableOwner` that
terminates the Workflow in error. A blank reply is a malformed answer and stays
retryable. The Activity retry policy caps transient retries (max 3 attempts,
bounded exponential backoff) and lists `NoSuitableOwner` under `doNotRetry`.

Vocabulary is neutral: fields and statuses say "owner", never "dev".

**Why:** the replay-to-repair demo replays a real event history to reproduce
and fix an Activity bug, so the bug sits in the Activity while the Workflow
stays deterministic and free of branching logic; a deliberate "no suitable
owner" verdict is a terminal business failure, not a transient error, so it
fails fast without wasting retries; neutral vocabulary keeps the demo
audience-agnostic.

**How to apply:** put any intentional bug in Activity code; keep the Workflow
linear with no conditional early exits, letting Activity failures propagate;
signal "no suitable owner" with the `none` token and raise it as a
non-retryable `NoSuitableOwner` failure; name fields and statuses with "owner".
