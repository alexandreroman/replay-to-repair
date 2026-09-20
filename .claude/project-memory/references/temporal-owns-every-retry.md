---
name: "Temporal owns every retry"
description: "The Activity retry policy is the only retry; both engines run with client-side retry off"
type: project
---

# Temporal owns every retry

The Activity retry policy declared in `IssueTriageWorkflowImpl` is the only
retry in this system: 5 maximum attempts with capped exponential backoff (1s
initial interval, ×2 coefficient, 10s ceiling), and `NoSuitableOwner` listed
as non-retryable. Both owner-selection engines call their model with
client-side retry disabled, so every attempt a triage makes is an Activity
attempt recorded in the event history.

The Spring AI engine reaches Anthropic through the official `anthropic-java`
SDK that Spring AI 2.x wraps, whose `ClientOptions.maxRetries` defaults to
`2` — three HTTP attempts per chat call unless configured.
`spring.ai.anthropic.max-retries: 0` in `application.yaml` turns that off. It
is a connection-level property (a sibling of `api-key`, not an option under
`spring.ai.anthropic.chat`), and `0` is honoured rather than read as unset.
The `spring.ai.retry.*` properties belong to a `RetryTemplate` that Spring AI
2.x does not ship.

The Jev engine reaches the same outcome by pinning the JDK HTTP client — see
[[jev-wire-format]].

**Why:** a retry layer underneath Temporal's hides attempts from the event
history, which is what the demo replays: a triage that took two HTTP attempts
inside one Activity attempt reads as a single attempt in the history, and the
wall-clock cost of those hidden attempts eats into the Activity's 30s
start-to-close timeout.

**How to apply:** bound retries by editing the `RetryOptions` in
`IssueTriageWorkflowImpl`, never by enabling retry in an engine's HTTP client
or model client. When adding an owner-selection engine, check what its
transport retries by default and disable it explicitly. A captured replay
fixture records the retry policy of the execution it captured; that policy is
outside Temporal's determinism check, so it does not invalidate the fixture —
see [[replay-fixture-reason-sync]].
