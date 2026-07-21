# Project Memory

> When a new decision **contradicts** an existing
> memory note, do NOT silently override it.
> Instead: surface the conflict, quote the
> existing memory, explain how the new decision
> differs, and ask for explicit confirmation
> before updating. **Do NOT take any action** —
> no tool calls, no file writes — until confirmed.

- [Generated text must be in English](references/generated-text-language.md) — all code & docs are written in English regardless of conversation language
- [Architecture conventions](references/architecture-conventions.md) — no persistence, no shared Maven module, fully API-driven frontend
- [Demo design constraints](references/demo-design-constraints.md) — bug lives in the Activity, Workflow runs to completion, neutral "owner" vocabulary
- [Code style conventions](references/code-style-conventions.md) — 80 cols text / 120 code; latest stable deps (verify via context7); no blank line after a class declaration
- [Project status](references/project-status.md) — implementation progress and the next step (single place tracking done vs. remaining)
- [Docker and Podman compatibility](references/docker-podman-compatibility.md) — runs on Docker (preferred) and Podman with no user intervention (host alias + COMPOSE auto-detect)
