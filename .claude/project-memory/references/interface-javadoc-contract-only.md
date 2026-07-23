---
name: "Interface Javadoc states the contract only"
description: "Activity/interface Javadoc describes the caller-facing contract, never implementation or demo details"
type: feedback
---

# Interface Javadoc states the contract only

Javadoc on an interface method (e.g. the Temporal `@ActivityInterface`
`TriageActivities` methods) describes only the caller-facing contract: what the
operation does and its observable outcome. It never mentions how the
implementation achieves it or demo-specific behavior — no "makes no real
external call", no simulated pauses/sleeps, no dashboard, frontend polling, or
step-visibility rationale. Those concerns belong in the implementation class,
not the contract.

**Why:** the interface is the contract seen by every caller; leaking
implementation or demo details into it couples the contract to one
implementation and misleads readers about what they can rely on.

**How to apply:** when writing or reviewing Javadoc on an interface method,
keep it to the contract (inputs, effect, outcome, failure modes). Move any
"how" or demo-only explanation to the implementing class's method body or its
own doc. Note that pre-existing methods may still carry implementation detail
in their interface Javadoc; new and edited docs follow the contract-only rule.
