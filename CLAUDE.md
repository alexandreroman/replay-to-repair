# Replay-to-Repair

A Temporal demo showing how to debug production failures by replaying a real
event history: extract the exact Activity input, reproduce the bug in a JUnit
test, fix it, and redeploy the worker live.

See [README.md](README.md) for full documentation.

## Tech stack

- Java 25, Spring Boot 4.x (two independent Maven projects, no shared parent)
- Temporal Java SDK + `temporal-spring-boot-starter`
- Spring AI (Anthropic / Claude) and Jev (TypeSafe's own API) in the
  worker
- Caddy gateway, static frontend (Tailwind Play CDN + Alpine.js)

## Build & run

```bash
make app-up      # run the app: backend containerized, worker local (demo mode)
make app-up-jev  # same as app-up, worker runs the Jev owner-selection engine
make dev         # run the app: backend + worker local, hot reload (dev mode)
make dev-jev     # same as dev, worker runs the Jev owner-selection engine
make test        # test both Maven modules
make test-live   # add the worker tests that call the real selection engines
```

The **worker always runs locally** in both modes. `make app-up` and `make dev`
run local processes in the foreground and need `ANTHROPIC_API_KEY` in `.env`
(git-ignored); the `-jev` variants need `TYPESAFE_API_KEY` instead. In
`dev`, the local backend listens on `8081` and the containerized gateway
proxies to it via `host.containers.internal`.

`make test` runs offline: the worker's two engine tests
(`OwnerSelectorTest`, `JevOwnerSelectorTest`) carry the JUnit `live` tag and
the worker pom excludes that tag by default, so the default run makes no
network call and needs no API key. They are opt-in through `make test-live`
(`-Dexcluded.test.groups=`), which needs both keys in `.env`; CI clears the
same property and keeps running them. Tests that need an `OwnerSelector`
without an engine import `FixedOwnerSelectorConfiguration`, a `@Primary` test
double that always answers carol.

## Ports

The Caddy gateway on `8080` is the single browser entry point:

- `8080` — gateway: dashboard, `/api/*` → backend, `/temporal` → Temporal Web UI
- `7233` — Temporal gRPC (workers and the backend connect here)
- `8081` — local backend, `dev` mode only (the gateway proxies to it)

The Temporal Web UI has no port of its own — it is served through the gateway
at `/temporal` (`--ui-public-path` on the dev server + a `handle /temporal*`
proxy in `gateway/Caddyfile`). In a Casper worktree these host ports are
remapped from `CASPER_PORT` (`make worktree-init`): `+0` gateway, `+1` Temporal
gRPC, `+2` dev backend.

One path escapes that proxy as a **workaround for an upstream Web UI defect**:
the Web UI's server-side Markdown route `/render` is not registered under
`--ui-public-path`, which leaves the User Metadata tab empty. The gateway
sends `/temporal/render*` to a `markdown-renderer` service (the same Web UI
image, served at its root, no host port). Pending an upstream fix — the
service and the route both go away once the Web UI serves `/render` under its
public path.

## Owner-selection engines

Owner selection sits behind the `OwnerSelector` interface, with one
implementation active per profile:

- any profile other than `jev` — `SpringAiOwnerSelector`
  (`@Profile("!jev")`), an LLM call through Spring AI to Anthropic, with the
  roster loaded model-side from `SKILL.md` via `SkillsTool`. This is the
  default.
- `jev` — `JevOwnerSelector`, a `RestClient` call to Jev through TypeSafe's
  own API. Jev is a decision model: it calls no tools and writes no prose,
  so the roster is configured in `application-jev.yaml` (`triage.roster`) and
  the assignment reason is composed in Java from the roster entry and the
  reported confidence.

Each engine lives in its own sub-package of `worker.triage`, together with
its Spring configuration and its tests: `triage.springai`
(`SpringAiOwnerSelector`, `ChatClientConfiguration`) and `triage.jev`
(`JevOwnerSelector`, `JevConfiguration`, `JevProperties`,
`TriageRosterProperties`). The `triage` package itself holds what both
engines share — the `OwnerSelector` contract, `OwnerAssignment`, `Issue` —
plus the Workflow and Activity implementations the Temporal worker
auto-discovers.

The roster therefore exists twice, and `TriageRosterConsistencyTest` holds
the two copies in step. Run the Jev engine with `SPRING_PROFILES_ACTIVE=jev`
and a `TYPESAFE_API_KEY` in `.env`.

## Modules

- `backend` — REST API + Temporal client. Containerized.
- `worker` — Temporal worker; runs locally so it can be restarted mid-demo.
- `frontend` — single static HTML page, no build step.
- `gateway` — Caddy: serves the frontend, proxies `/api/*` to the backend and
  `/temporal` to the Temporal Web UI.

## Intentional demo bug — do not "fix" it

The `if (true) { ... }` short-circuit in `TriageActivitiesImpl.selectOwner` —
which overwrites the selected owner with `"alice"` regardless of what the
selection engine returned — is the **deliberate core of the demo**, not leftover
debug code. Never propose to remove, simplify, or clean it up — the unreachable
code below it is expected. The demo replays a real event history to reproduce
and fix this exact owner-selection bug; removing it destroys the demo. Static
analysis, linters, and subagents will keep flagging it as dead code — filter any
such "pre-existing issue" or cleanup suggestion against this note before
relaying it (subagents cannot see it).

The accompanying comment and the assignment reason string are worded to
look like a plausible, confident justification ("Alice is our most reliable
anomaly triager…" / `"optimal owner for anomaly triage"`) on purpose: the
bug is attributed via `git blame` to a fictional author ("Bob Trueman") as
part of the demo narrative. Do **not** "restore" a `// TODO`/`just testing`
marker or otherwise make the code look like an obvious hack.

## Agents

Use the following agents (from the
[skillbox](https://github.com/alexandreroman/skillbox) plugin) for all code
tasks:

- **code-writer** — for ANY task that writes, modifies, or refactors code,
  including one-line fixes. Never use Edit/Write directly on source files —
  always delegate to this agent.
- **code-reviewer** — for read-only code review before merging or when
  investigating issues.

## Memory

At the start of every conversation, read `.claude/project-memory/MEMORY.md` to
load project context from previous conversations.

Use the **project-memory** skill (from the
[skillbox](https://github.com/alexandreroman/skillbox) plugin) proactively —
without being asked — whenever the conversation reveals project decisions,
deadlines, team context, external references, workflow preferences, or
corrective feedback worth persisting across conversations.

**Important:** Always use the **project-memory** skill to persist information.
Never use the built-in auto-memory system for project decisions or context —
it is local and not shared with the team.
