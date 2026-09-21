---
name: "Project status"
description: "Current implementation progress and the next implementation step"
type: project
---

# Project status

As of 2026-09-21, the demo is feature-complete and both Maven modules build
green.

Implemented and committed:

- `IssueTriageWorkflow` + `TriageActivities` (`selectOwner`/`updateTicket`/
  `notifyAssignment` regular). `updateTicket` runs within the `OWNER_SELECTED`
  step (no new `Step` enum value): it records the assigned owner on the issue's
  existing ticket and simulates a ~2s ticketing-system call. Owner selection is
  delegated to a Temporal-agnostic `OwnerSelector` component that returns
  `Optional<OwnerAssignment>` (the chosen owner and a short reason for the
  pick), genuinely calling the LLM for every issue. The `selectOwner` Activity
  holds the intentional `if (true) { assignment = new OwnerAssignment("alice",
  "optimal owner for anomaly triage"); }` bug immediately before its
  `triage.owner.selected` log, overwriting whatever `OwnerSelector` returned;
  `TriageActivitiesImpl` raises the non-retryable `NoSuitableOwner` failure when
  the selection is empty. The workflow stores the reason at the workflow level
  in `TriageStatus.assignmentReason` (worker and backend copies identical); the
  reason is deliberately not surfaced by the REST API (`IssueView`) or the
  dashboard.
- Owner selection runs through one of two engines behind the `OwnerSelector`
  interface, selected by Spring profile (see [[skills-tool-owner-roster]] and
  [[demo-design-constraints]]). `SpringAiOwnerSelector` (`@Profile("!jev")`, the
  default) calls Claude through Spring AI, with the roster loaded model-side
  from `SKILL.md` via `SkillsTool`; its output contract covers the chosen owner
  (or the `none` token when no owner fits) plus a one-sentence reason, parsed
  defensively with Jackson (unknown fields ignored, explicit property names,
  common key variants mapped via aliases). `JevOwnerSelector`
  (`@Profile("jev")`) calls Jev, a decision model, through TypeSafe's own API at
  `POST https://api.typesafe.ai/v1/systemone`, with the roster configured in
  `application-jev.yaml` (`triage.roster`); because Jev calls no tools and
  writes no prose, the assignment reason is composed in Java from the matched
  roster entry's specialties and the reported confidence. The HTTP call goes
  through `JevClient` (`triage.jev.client`), which owns the wire format and
  its own connection settings and covers Jev's three question types —
  `choice`, `score` and `noul`, batched in one call — though owner selection
  asks only a `choice` (see [[jev-wire-format]]).
  `TriageRosterConsistencyTest` keeps the SKILL.md table and the configured
  roster in step, comparing owner names, specialties, and preferences cell by
  cell. Both engines map `none` to an empty `Optional` (the deliberate
  no-suitable-owner verdict) and throw on a malformed answer.
- Backend REST API (`POST /api/v1/issues/generate`, `GET /api/v1/issues`) and
  the Alpine.js dashboard, served through the Caddy gateway. The dashboard shows
  a distinct `FAILED` state for terminal, non-completed workflows (e.g. the
  `NoSuitableOwner` failure), rather than the neutral "received" placeholder.
  `IssueView` carries the issue id, read from `TriageStatus` when the execution
  resolves and from the memo otherwise, which the API exposes without the
  dashboard rendering it. It also carries a `workflowUrl` field: a same-origin
  Temporal Web UI deep-link (`/temporal/namespaces/{namespace}/workflows/{workflowId}/{runId}/history`)
  built server-side with the namespace read from the client, so the frontend
  hardcodes nothing. Each card title is an anchor to that link, styled to render
  identically until hover (underline on hover only).
- Temporal Web UI proxied at `/temporal`, with executions labelled through
  Temporal user metadata: a static summary and Markdown static details at
  start, a summary per Activity, and current details published at each `Step`
  (see [[temporal-user-metadata-ui-only]]). A `markdown-renderer` container
  serves the Web UI's `/render` route, which the gateway routes
  `/temporal/render*` to so the User Metadata tab renders.
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
  matrix over `[backend, worker]` on Temurin 25 with
  `./mvnw -B verify -Dexcluded.test.groups=`, which adds the worker's
  `live`-tagged engine tests to the run; it builds no container images. The
  worker's live-tagged tests read `ANTHROPIC_API_KEY` and
  `TYPESAFE_API_KEY` from repository secrets of the same names — the former
  for the default Spring AI engine, the latter for the jev-profile tests calling
  Jev through TypeSafe's own API — and both secrets must be configured for the
  worker job to pass. A `paths-ignore` filter on the push/PR triggers skips
  runs for commits that touch only docs or UI (`**.md`, `.claude/**`,
  `frontend/**`, `gateway/**`, `LICENSE`, `.gitignore`); `workflow_dispatch`
  is unfiltered so manual runs always run.

The worker suite is 51/51 green with the intentional bug committed. `make
test` runs 42 of them offline in about eight seconds: `OwnerSelectorTest`,
`JevOwnerSelectorTest` and `JevClientTest` carry the JUnit `live` tag and
`worker/pom.xml` excludes that tag through the `excluded.test.groups`
property, so the default run makes no network call and needs no API key.
`make test-live` and CI clear the property and run all 51.
`OwnerSelectorTest` (a `@SpringBootTest` exercising the real
`SpringAiOwnerSelector` bean with the injected `ChatClient`) and
`JevOwnerSelectorTest` (a `@SpringBootTest` under the `jev` profile, calling Jev
through TypeSafe's own API for real) each assert genuine engine-driven owner
selection per issue category (e.g. alice for backend issues, carol for security
issues), unaffected by the short-circuit. `JevOwnerSelectorOfflineTest`
exercises `JevOwnerSelector`'s response-parsing branches (the `none` verdict, a
blank or off-roster choice, a missing answer, an HTTP error, unknown root
fields) against a `MockRestServiceServer` stand-in for TypeSafe's API, with no
network call and no API key. `JevClientOfflineTest` pins the wire format
itself against JSON captured from the live API — the criteria shape and the
answer shape of each question type, a mixed batch, and the client's error
paths — while the live `JevClientTest` checks all three answer shapes against
the real API in a single call. `OwnerSelectorProfileTest` pins
`SpringAiOwnerSelector` as the default
engine when no profile is set. `TriageActivitiesImplTest` runs under the `test`
profile with `OwnerSelector` mocked to return a different owner (carol),
proving the Activity's override: it asserts the Activity still returns
`alice`/`"optimal owner for anomaly triage"`. `IssueTriageWorkflowTest` (a
`@SpringBootTest` running the workflow on the Temporal test server, with owner
selection on `FixedOwnerSelector`, a `@Primary` test double answering carol
that tests import explicitly rather than bind to the `test` profile, so
`OwnerSelectorProfileTest` still resolves the real engine) and
`IssueTriageWorkflowReplayTest` (which replays the committed history) both still
observe `alice` end-to-end. All stay green with the committed short-circuit.

No implementation work is outstanding.

**Why:** the demo narrative depends on these pieces; tracking progress here
keeps the status out of the README (which describes the target end state).

**How to apply:** update this note as milestones land — treat it as the single
place recording what is done versus what remains, and update the date when the
status changes.
