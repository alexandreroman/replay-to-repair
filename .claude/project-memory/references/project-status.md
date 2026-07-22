---
name: "Project status"
description: "Current implementation progress and the next implementation step"
type: project
---

# Project status

As of 2026-07-23, the demo is feature-complete and every change is committed;
the working tree is clean and both Maven modules build green.

Implemented and committed:

- `IssueTriageWorkflow` + `TriageActivities` (`selectOwner`/`notifyAssignment`
  regular). Owner selection is delegated to a Temporal-agnostic `OwnerSelector`
  component that returns `Optional<String>` and holds the intentional
  `if (true) { return Optional.of("alice"); }` bug; `TriageActivitiesImpl`
  raises the non-retryable `NoSuitableOwner` failure when the result is empty.
- Owner-selection roster and rules loaded via `SkillsTool` from a single
  `SKILL.md` (see [[skills-tool-owner-roster]]). The skill returns the `none`
  token when no owner fits; `OwnerSelector` maps it to an empty `Optional` and
  `TriageActivitiesImpl` raises a non-retryable `NoSuitableOwner` failure that
  terminates the workflow, and the activity retry policy is bounded (see
  [[demo-design-constraints]]).
- Backend REST API (`POST /api/v1/issues/generate`, `GET /api/v1/issues`) and
  the Alpine.js dashboard, served through the Caddy gateway. The dashboard shows
  a distinct `FAILED` state for terminal, non-completed workflows (e.g. the
  `NoSuitableOwner` failure), rather than the neutral "received" placeholder.
- Temporal Web UI proxied at `/temporal`.
- A committed event-history fixture
  (`worker/src/test/resources/history/issue-triage.json`) and a single
  `IssueTriageWorkflowReplayTest` that replays it against the workflow with
  `WorkflowReplayer`. The test runs in `make test` (it guards workflow
  determinism); for the demo it also runs from the IDE with breakpoints. If the
  workflow changes incompatibly with the committed history, the replay goes red
  until the fixture is refreshed. The `make capture-history` target refreshes the
  fixture from the latest `IssueTriageWorkflow` (Web UI Download and
  `temporal workflow show --output json` are the manual routes). The demo's
  Temporal visibility store rejects `ORDER BY` in list queries, so the target
  relies on the default newest-first ordering with `--limit 1`.
- README with the full demo narrative, and ECS structured logging across all
  processes (see [[ecs-logging-all-processes]]).
- A GitHub Actions CI workflow (`.github/workflows/build.yml`) that builds and
  tests both modules on push/PR to `main` (and manual dispatch). It runs a
  matrix over `[backend, worker]` on Temurin 25 with `./mvnw -B verify`; it
  builds no container images. The worker's LLM-backed tests read
  `ANTHROPIC_API_KEY` from a repository secret of the same name, which must be
  configured for the worker job to pass.

`make test` stays green with the intentional bug committed. `OwnerSelectorTest`
(a `@SpringBootTest` exercising the real `OwnerSelector` bean with the injected
`ChatClient`) and `IssueTriageWorkflowTest` (a `@SpringBootTest` running the
workflow on the Temporal test server) exercise the short-circuit and assert
that `alice` is selected, so they pass while the bug is present;
`IssueTriageWorkflowReplayTest` replays the committed history and guards workflow
determinism. All stay green with the committed short-circuit.

No implementation work is outstanding.

**Why:** the demo narrative depends on these pieces; tracking progress here
keeps the status out of the README (which describes the target end state).

**How to apply:** update this note as milestones land — treat it as the single
place recording what is done versus what remains, and update the date when the
status changes.
