---
name: "Replay fixture embeds the owner-selection reason string"
description: "The committed event-history fixture hard-codes OwnerSelector's reason; keep it in sync via make capture-history"
type: project
---

# Replay fixture embeds the owner-selection reason string

The committed replay fixture
(`worker/src/test/resources/history/issue-triage.json`) encodes
`OwnerSelector`'s reason string in two payloads: the `selectOwner`
activity result (`{"owner":"alice","reason":"..."}`) and the final
workflow result (`assignmentReason`). Both must match the reason
returned by `OwnerSelector.select` — currently
`"optimal owner for anomaly triage"` (see [[project-status]],
[[demo-design-constraints]]).

**Why:** the reason is workflow/activity data, not a command input, so
it does not affect replay determinism — `IssueTriageWorkflowReplayTest`
stays green even when the fixture's reason drifts from the source. The
demo has the presenter download and inspect this exact history in the
Temporal Web UI, so a stale reason (e.g. an obvious-hack marker) silently
contradicts the deliberately confident wording.

**How to apply:** after rewording the reason in `OwnerSelector`,
regenerate the fixture with `make capture-history` (stack up, one triage
workflow completed) rather than trusting the test suite to catch the
drift. Confirm both payloads decode to the new reason.
