# Replay-to-Repair

Temporal durably records the complete event history of every Workflow
Execution — every input and every result, in order. That unlocks something no
ordinary system can offer: take the exact history of a failure that happened in
**production** and replay it, deterministically, on a **local dev machine** —
stepping through the real execution in a debugger with the very payload that
triggered the bug.

This is a conference/workshop demo built on that capability. It turns a Temporal
event history into a permanent regression test: replay the **exact payload**
that flowed through the system — no guessing, no synthetic data — reproduce the
bug locally, write a test from the captured input, fix it, and redeploy under
real conditions.

The scenario: an AI triage agent assigns incoming issues to developers
("owners") based on their specialties. In production, every recent issue lands
on the same owner. The root cause is a debug line accidentally committed in the
owner-selection Activity — an early return that short-circuits the LLM call.
Once the source of the failure is pinpointed in the **Activity**, the fix
becomes straightforward — and it ships to production faster.

## Prerequisites

- **JDK 25** (each module ships a Maven wrapper — no separate Maven install)
- **Docker** or **Podman** with the Compose plugin
- **Anthropic API key** — the owner-selection Activity calls Claude via
  Spring AI
- **Temporal CLI** (optional) — handy for inspecting workflows; the Web UI at
  <http://localhost:8233> covers the demo needs

## Getting Started

```bash
# 1. Provide your Anthropic API key (git-ignored)
echo 'ANTHROPIC_API_KEY=your-api-key' > .env.local

# 2. Launch the app (Ctrl-C to stop the local processes)
make app-up

# 3. Open the dashboard
open http://localhost:8080
```

The Temporal Web UI is available at <http://localhost:8233>.

### Two ways to run

The **worker always runs locally** (never containerized) so it can be rebuilt
and redeployed in seconds during the demo. The two entry points differ only in
where the backend runs:

- `make app-up` — backend, gateway, and Temporal run in containers; the worker
  runs locally. This is the demo topology (redeploy the worker while the rest
  keeps running).
- `make dev` — backend **and** worker run locally with hot reload (for IDE
  debugging); only Temporal and the gateway stay in containers.

Both serve the dashboard at <http://localhost:8080> and run the local processes
in the foreground; press Ctrl-C to stop them, then `make app-down` to remove the
containers. Run `make` (or `make help`) to list every target.

### Casper

Casper runs each workspace as an isolated Git worktree, so several workspaces
can run this demo at once. Each workspace automatically gets its own host ports
(gateway, Temporal, and backend, all derived from the injected `CASPER_PORT`)
via the `setup` hook, which runs `make worktree-init`, so parallel workspaces
never collide. `teardown` stops the containers when a workspace is closed. A
plain (non-Casper) checkout keeps the default ports unchanged.

| Command            | Runs         | Description                                  |
| ------------------ | ------------ | -------------------------------------------- |
| `casper run`       | `make app-up`| Launch the demo (backend containerized)      |
| `casper run dev`   | `make dev`   | Local hot-reload mode (backend + worker)     |
| `casper run test`  | `make test`  | Run the test suite for both Maven modules    |

## The demo

The end-to-end narrative the tooling drives (target flow):

1. Trigger a batch of issues from the dashboard → everything lands on a single
   owner.
2. Find the failing Workflow Execution in the Temporal Web UI and download its
   event history as JSON.
3. Load the history, locate the owner-selection Activity's scheduled input, and
   decode it back into a real object.
4. Run a JUnit test that exercises the Activity directly with that captured
   input (set a breakpoint), asserting a different owner should have been
   chosen → red.
5. Remove the debug early return → fix the bug.
6. Re-run the same test → green.
7. Rebuild and redeploy the worker only — the backend, gateway, and dashboard
   keep running throughout.
8. Submit a new issue under real conditions → verify a correct distribution of
   assignments.

## Usage

```bash
make app-up      # run the app: backend containerized, worker local (demo mode)
make dev         # run the app: backend + worker local, hot reload (dev mode)
make app-down    # stop and remove the containers
make infra-up    # start only Temporal + gateway in containers
make infra-down  # stop Temporal + gateway
make test        # run the test suite for both Maven modules
make build       # build the production JARs for both modules
```

## Configuration

| Variable            | Description                          | Default          |
| ------------------- | ------------------------------------ | ---------------- |
| `ANTHROPIC_API_KEY` | Anthropic key for the worker's LLM   | (required)       |
| `ANTHROPIC_MODEL`   | Claude model used for owner triage   | `claude-sonnet-5`|
| `TEMPORAL_ADDRESS`  | Temporal gRPC endpoint               | `localhost:7233` |
| `TEMPORAL_NAMESPACE`| Temporal namespace                   | `default`        |
| `PORT`              | Backend HTTP port                    | `8080`           |

Put local values in `.env.local` (git-ignored); `make` loads it automatically
for dev and test targets.

## Architecture

```mermaid
graph TD
    Browser -->|HTTP| Gateway[Caddy gateway]
    Gateway -->|static files| Frontend[Static dashboard]
    Gateway -->|/api/*| Backend[Backend REST API]
    Backend -->|start / query workflows| Temporal[(Temporal server)]
    Worker[Worker - local process] -->|poll task queue| Temporal
    Worker -->|owner selection| Claude[Anthropic Claude]
```

The `backend` and `worker` are independent Maven projects with no shared parent
or common module — shared types are duplicated in each rather than extracted.

| Module     | Description                                                     |
| ---------- | --------------------------------------------------------------- |
| `backend`  | Spring Boot REST API; Temporal client that starts/queries flows |
| `worker`   | Spring Boot Temporal worker; hosts the triage workflow/activity |
| `frontend` | Static dashboard (Tailwind CDN + Alpine.js), no build step      |
| `gateway`  | Caddy config serving the frontend and proxying `/api/*`         |

## License

This project is licensed under the Apache-2.0 License — see [LICENSE](LICENSE)
for details.
