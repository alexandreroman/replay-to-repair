# Project Memory

> When a new decision **contradicts** an existing
> memory note, do NOT silently override it.
> Instead: surface the conflict, quote the
> existing memory, explain how the new decision
> differs, and ask for explicit confirmation
> before updating. **Do NOT take any action** —
> no tool calls, no file writes — until confirmed.

> **Note wording** — state permanent facts in the
> present tense. A note read out of context must
> not reveal what it replaces or what just
> happened. Ban narration markers: "now", "no
> longer", "previously / used to", "reverses /
> replaces", "kept", "changed to", "reintroduce",
> "the user asked to". Phrase prohibitions
> positively ("ECS applies unconditionally"), not
> as the negation of a former state. Test: remove
> the note from its context — if a sentence only
> makes sense knowing the prior state, rewrite it.

- [Generated text must be in English](references/generated-text-language.md) — all code & docs are written in English regardless of conversation language
- [Architecture conventions](references/architecture-conventions.md) — no persistence, no shared Maven module, fully API-driven frontend
- [Demo design constraints](references/demo-design-constraints.md) — bug lives in the Activity, Workflow stays linear; no-owner (`none` token) fails it non-retryably (NoSuitableOwner); neutral "owner" vocabulary
- [Code style conventions](references/code-style-conventions.md) — 80 cols text / 120 code; latest stable deps (verify via context7); no blank line after a class declaration; present-tense code comments, no history narration
- [Project status](references/project-status.md) — implementation progress and the next step (single place tracking done vs. remaining)
- [Docker and Podman compatibility](references/docker-podman-compatibility.md) — runs on Docker (preferred) and Podman with no user intervention (host alias + COMPOSE auto-detect)
- [Spring AI structured output over raw String](references/spring-ai-structured-output.md) — use call().entity(record) to constrain the LLM to JSON rather than parsing a String
- [Logging conventions](references/logging-conventions.md) — SLF4J 2.x fluent API with addKeyValue context; .log() message is a dotted domain code, not a sentence; Workflow.getLogger in workflow code (replay-safe)
- [ECS log format for all processes, always](references/ecs-logging-all-processes.md) — backend + worker emit ECS structured console logs unconditionally, including local/dev mode
- [Owner roster in a SkillsTool skill](references/skills-tool-owner-roster.md) — roster+rules live in SKILL.md loaded via spring-ai-agent-utils SkillsTool; build() returns ToolCallback
- [README must not mention Casper](references/readme-no-casper.md) — keep Casper out of the public README; Casper port-remap docs live in CLAUDE.md only
- [Never print .env contents](references/never-print-env-contents.md) — never cat/echo `.env`/`.env.local`; they hold a live ANTHROPIC_API_KEY; check presence without revealing values
