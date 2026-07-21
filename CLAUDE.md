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

## Modules

- `backend` — REST API + Temporal client. Containerized.
- `worker` — Temporal worker; runs locally so it can be restarted mid-demo.
- `frontend` — single static HTML page, no build step.
- `gateway` — Caddy: serves the frontend, proxies `/api/*` to the backend.

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
