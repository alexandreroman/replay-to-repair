---
name: "TEMPORAL_DEBUG disables the deadlock detector for IDE debugging"
description: "Set TEMPORAL_DEBUG=true to debug workflows/replay with breakpoints without tripping PotentialDeadlockException (TMPRL1101)"
type: reference
---

# TEMPORAL_DEBUG disables the deadlock detector for IDE debugging

The Temporal Java SDK requires a workflow thread to yield control within one
second per Workflow Task. Pausing on a breakpoint while debugging workflow code
— including a `WorkflowReplayer` replay such as `IssueTriageWorkflowReplayTest`
— freezes that thread past the limit, so the SDK raises a
`PotentialDeadlockException` ([TMPRL1101]) even though nothing is actually
wrong. The reported line is just where execution paused, and the failure
surfaces as a `__replay_only` query error because replay drives the workflow
through an internal query.

**Why:** the deadlock detector cannot tell a debugger pause from a genuine
block, so any breakpoint held longer than a second trips it. This is not a
non-determinism error and not a real deadlock.

**How to access:** set `TEMPORAL_DEBUG=true` for the debug run — as an
environment variable or as a `-DTEMPORAL_DEBUG=true` JVM system property in the
IDE run/debug configuration; the SDK honors both. Keep it OFF for `make test`
and CI so real deadlock detection stays active. See [[demo-design-constraints]]
for the replay test's role.
