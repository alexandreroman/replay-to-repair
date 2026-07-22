---
name: "Demo design constraints"
description: "Bug lives in the Activity, Workflow stays linear, no-owner fails it non-retryably, neutral owner vocabulary"
type: project
---

# Demo design constraints

The demonstrated bug always lives in activity-side code, never in the Workflow.
Owner selection is delegated by the `selectOwner` Activity to a
Temporal-agnostic `OwnerSelector` component, and the intentional bug lives in
that component; it runs during Activity execution, not in Workflow code. The
Workflow code stays linear: it holds no conditional branching or early return
and delegates every decision to Activities. It completes normally on the happy
path, and a non-retryable Activity failure terminates it in error.

The `selectOwner` Activity assigns an owner from the roster. The `OwnerSelector`
component returns `Optional<String>` and stays Temporal-agnostic: the `none`
token from the LLM becomes an empty `Optional` (a deliberate no-suitable-owner
verdict), while a blank reply throws (a malformed answer). The Activity turns an
empty `Optional` into a non-retryable `ApplicationFailure` of type
`NoSuitableOwner` that terminates the Workflow in error; a thrown
malformed-reply error stays retryable. The Activity retry policy bounds
transient retries and does not retry `NoSuitableOwner`.

Vocabulary is neutral: fields and statuses say "owner", never "dev".

**Why:** the replay-to-repair demo replays a real event history to reproduce
and fix an Activity bug, so the bug sits in the Activity while the Workflow
stays deterministic and free of branching logic; a deliberate "no suitable
owner" verdict is a terminal business failure, not a transient error, so it
fails fast without wasting retries; neutral vocabulary keeps the demo
audience-agnostic.

**How to apply:** put any intentional bug in activity-side code (the
`OwnerSelector` component or the Activity), never in the Workflow; keep the
Workflow linear with no conditional early exits, letting Activity failures
propagate; keep `OwnerSelector` Temporal-agnostic by returning an empty
`Optional` for the `none` verdict and let the Activity raise the non-retryable
`NoSuitableOwner` failure; name fields and statuses with "owner".
