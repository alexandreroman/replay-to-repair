---
name: "Project status"
description: "Current implementation progress and the next implementation step"
type: project
---

# Project status

As of 2026-07-22, the demo is feature-complete and every change is committed;
the working tree is clean and both Maven modules build green.

Implemented and committed:

- `IssueTriageWorkflow` + `TriageActivities` (`loadProfiles` local,
  `selectOwner`/`notifyAssignment` regular), with the intentional
  `if (true) { return "alice"; }` bug in `selectOwner`.
- Owner-selection roster and rules loaded via `SkillsTool` from a single
  `SKILL.md` (see [[skills-tool-owner-roster]]). The skill returns the `none`
  token when no owner fits; `selectOwner` raises a non-retryable
  `NoSuitableOwner` failure that terminates the workflow, and the activity
  retry policy is bounded (see [[demo-design-constraints]]).
- Backend REST API (`POST /api/v1/issues/generate`, `GET /api/v1/issues`) and
  the Alpine.js dashboard, served through the Caddy gateway.
- Temporal Web UI proxied at `/temporal`.
- Replay tooling (`SelectOwnerHistoryExtractor`) and a committed history
  fixture (`worker/src/test/resources/history/select-owner-failure.json`).
- README with the full demo narrative, and ECS structured logging across all
  processes (see [[ecs-logging-all-processes]]).

Two `@Disabled` tests stay disabled by design so `make test` is green while the
bug is committed: `SelectOwnerReplayTest` (the replay demo, RED with the bug and
GREEN once the debug line is removed) and `GenerateHistoryFixtureTest` (the
fixture generator). Run them from the IDE for the demo.

No implementation work is outstanding.

**Why:** the demo narrative depends on these pieces; tracking progress here
keeps the status out of the README (which describes the target end state).

**How to apply:** update this note as milestones land — treat it as the single
place recording what is done versus what remains, and update the date when the
status changes.
