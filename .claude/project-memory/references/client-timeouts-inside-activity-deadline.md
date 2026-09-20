---
name: "Client timeouts fit the Activity deadline"
description: "Both owner-selection engines share a 20s total HTTP budget, inside the Activity's 30s start-to-close timeout"
type: project
---

# Client timeouts fit the Activity deadline

Both owner-selection engines bound their model call to the same 20s total
budget, comfortably below the 30s `startToCloseTimeout` declared in
`IssueTriageWorkflowImpl`: the Spring AI engine with
`spring.ai.anthropic.timeout: 20s` in `application.yaml`, the Jev engine with
a 5s connect + 15s read timeout in `application-jev.yaml`.

`spring.ai.anthropic.timeout` is a `java.time.Duration` on
`AnthropicConnectionProperties` — a connection-level sibling of `api-key`, not
an option under `spring.ai.anthropic.chat`.
`AnthropicChatAutoConfiguration` forwards it to `AnthropicSetup`, which turns
it into the Anthropic SDK's `Timeout.request(...)`; OkHttp applies that as its
`callTimeout`, a cap on the whole call — DNS, connect, write and read. That
single property is therefore the engine's entire budget. Its default when unset
is 60s, twice the Activity deadline.

**Why:** whichever deadline fires first owns the error. A client-side timeout
fails the Activity attempt with a cause that names the real problem (a stalled
LLM or HTTP call), while Temporal's own deadline surfaces an opaque
`ActivityTaskTimedOut` that says nothing about what hung. The gap between the
two also leaves room for the attempt to be recorded before the Workflow gives
up — see [[temporal-owns-every-retry]].

**How to apply:** when adding an owner-selection engine, give it the same 20s
total budget, summing every phase its transport times out separately (connect,
read), and check what the transport's default is — most default higher. When
raising the budget, raise the `startToCloseTimeout` first so the ordering
holds. Comment each timeout with this rationale, mirroring the wording the two
profiles already share.
