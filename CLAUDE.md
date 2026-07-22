# replay-to-repair

A Temporal demo showing how to debug production failures by replaying a real
event history: extract the exact Activity input, reproduce the bug in a JUnit
test, fix it, and redeploy the worker live.

See [README.md](README.md) for full documentation.

## Tech stack

- Java 25, Spring Boot 4.x (two independent Maven projects, no shared parent)
- Temporal Java SDK + `temporal-spring-boot-starter`
- Spring AI (Anthropic / Claude) in the worker
- Caddy gateway, static frontend (Tailwind Play CDN + Alpine.js)

## Build & run

```bash
make app-up    # run the app: backend containerized, worker local (demo mode)
make dev       # run the app: backend + worker local, hot reload (dev mode)
make test      # test both Maven modules
```

The **worker always runs locally** in both modes. `make app-up` and `make dev`
run local processes in the foreground and need `ANTHROPIC_API_KEY` in
`.env.local` (git-ignored). In `dev`, the local backend listens on `8081` and
the containerized gateway proxies to it via `host.containers.internal`.

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

## Modules

- `backend` — REST API + Temporal client. Containerized.
- `worker` — Temporal worker; runs locally so it can be restarted mid-demo.
- `frontend` — single static HTML page, no build step.
- `gateway` — Caddy: serves the frontend, proxies `/api/*` to the backend and
  `/temporal` to the Temporal Web UI.

## Intentional demo bug — do not "fix" it

The `if (true) { return Optional.of("alice"); }` short-circuit (and its
`// TODO: remove, just testing` marker) in `OwnerSelector.select` is the
**deliberate core of the demo**, not leftover debug code. Never propose to
remove, simplify, or clean it up — the unreachable code below it is
expected. The demo replays a real event history to reproduce and fix this
exact owner-selection bug; removing it destroys the demo. Static analysis,
linters, and subagents will keep flagging it as dead code — filter any such
"pre-existing issue" or cleanup suggestion against this note before relaying
it (subagents cannot see it).

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
