---
name: "Workflow input wire names must match the replay fixture"
description: "Renaming an Issue record component changes the JSON keys and fails the replay test until the fixture is recaptured"
type: project
---

# Workflow input wire names must match the replay fixture

The `Issue` record components are the JSON keys of the workflow input, and the
committed fixture (`worker/src/test/resources/history/issue-triage.json`)
stores the payload produced by the record as it stood when the history was
captured. Renaming a component renames the wire key, and
`IssueTriageWorkflowReplayTest` then fails while deserializing the
`WorkflowExecutionStarted` input:
`UnrecognizedPropertyException: Unrecognized field "id" (class ... Issue)`.

`Issue` carries no `@JsonIgnoreProperties(ignoreUnknown = true)`, which is what
makes the mismatch loud. `TriageStatus` carries one because it is a result
shape crossing the two module copies.

**Why:** a lenient `Issue` would hand the workflow three null components and
replay a history that no longer describes the recorded execution — a silent
failure in the one test whose whole job is to prove the recorded execution
still replays.

**How to apply:** treat any rename of an `Issue` component as a fixture change.
Run the demo end to end and regenerate the history with `make capture-history`
(see [[replay-fixture-reason-sync]]), then confirm the fixture's input payload
decodes to the new keys before relying on a green suite.
