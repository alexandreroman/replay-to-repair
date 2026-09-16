---
name: "Temporal user metadata is UI-only"
description: "Static summary/details, activity summaries and current details label the Web UI; the memo stays the programmatic source"
type: project
---

# Temporal user metadata is UI-only

Temporal user metadata — the workflow static summary and static details, the
per-Activity summaries, and `Workflow.setCurrentDetails` — exists purely to
label executions in the Temporal Web UI. The memo set at start time is the
backend's programmatic data source: the dashboard reads the issue id, title
and description back from it through `listExecutions` when no worker runs. The
two carry the same issue data on purpose and are not deduplicated.

Where each piece is actually readable, verified against
server `temporalio/temporal:latest` (CLI 1.9.1, Server 1.32.0, UI 2.54.1):

- Static summary and details come back from `DescribeWorkflowExecution` under
  `executionConfig.userMetadata`, for closed executions too
  (`temporal workflow describe --output json`).
- Activity summaries label the bars of the workflow's **Timeline** tab, as
  `SelectOwner · Ask the LLM which roster owner fits the issue`.
- Current details travel only through the built-in
  `__temporal_workflow_metadata` Query, so reading them needs a live worker —
  the same constraint as the dashboard's own `getStatus` Query. A closed
  execution answers it too: the worker replays its history to serve the Query.
  `DescribeWorkflowExecution` does not carry them.
- The UI's **User Metadata** tab shows its Summary and Details in an iframe
  pointing at the UI's server-side Markdown route `/render?content=…`, which
  is not registered under `--ui-public-path` and answers the SPA shell behind
  `/temporal`. The gateway routes that single path to a `markdown-renderer`
  container serving the same Web UI at its root, which is what makes the tab
  render (see the comments in `compose.yaml` and `gateway/Caddyfile`).

User metadata does not produce a workflow command, so adding or changing it
leaves a committed replay fixture valid and `IssueTriageWorkflowReplayTest`
green (see [[workflow-input-wire-names-replay]] for what does break it).

Server limits: 200 bytes for a static summary, 20 KB for static details.
Per-Activity option overrides are keyed by Activity *type* name — the method
name with an upper-case first letter (`SelectOwner`, `UpdateTicket`,
`NotifyAssignment`) — and a key matching no Activity type is ignored silently.

**Why:** a Query needs a live worker, so the memo is the only issue data the
dashboard can read while the worker is offline mid-demo; summary and details
would be invisible to it. Keying per-Activity options by method name instead of
type name fails silently, which is easy to ship unnoticed.

**How to apply:** keep `setMemo` as the machine-readable channel and treat
summary/details/current details as display strings; demo the Activity
summaries from the Timeline tab, which labels each bar; truncate a static
summary to the 200-byte cap; derive per-Activity options from the shared
defaults via `toBuilder()` so the timeout and retry policy survive; spell
per-Activity keys with an upper-case first letter.
