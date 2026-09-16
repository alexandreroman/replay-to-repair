---
name: "Temporal user metadata is UI-only"
description: "Static summary/details, activity summaries and current details label the Web UI; the memo stays the programmatic source"
type: project
---

# Temporal user metadata is UI-only

Temporal user metadata — the workflow static summary and static details, the
per-Activity summaries, and `Workflow.setCurrentDetails` — exists purely to
label executions in the Temporal Web UI. The memo set at start time is the
backend's programmatic data source: the dashboard reads the issue title and
description back from it through `listExecutions` when no worker runs. The two
carry the same issue data on purpose and are not deduplicated.

User metadata does not produce a workflow command, so adding or changing it
leaves the committed replay fixture valid and `IssueTriageWorkflowReplayTest`
green.

Server limits: 200 bytes for a static summary, 20 KB for static details.
Per-Activity option overrides are keyed by Activity *type* name — the method
name with an upper-case first letter (`SelectOwner`, `UpdateTicket`,
`NotifyAssignment`) — and a key matching no Activity type is ignored silently.

**Why:** a Query needs a live worker, so the memo is the only issue data the
dashboard can read while the worker is offline mid-demo; summary and details
would be invisible to it. Keying per-Activity options by method name instead of
type name fails silently, which is easy to ship unnoticed.

**How to apply:** keep `setMemo` as the machine-readable channel and treat
summary/details/current details as display strings; truncate a static summary
to the 200-byte cap; derive per-Activity options from the shared defaults via
`toBuilder()` so the timeout and retry policy survive; spell per-Activity keys
with an upper-case first letter.
