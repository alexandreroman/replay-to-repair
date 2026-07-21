---
name: "Owner roster lives in an agentskills.io skill loaded via SkillsTool"
description: "Triage roster + rules are a SKILL.md exposed to the LLM as a tool; build() returns ToolCallback"
type: project
---

# Owner roster lives in an agentskills.io skill loaded via SkillsTool

The issue-triage owner roster and the selection methodology live in a single
agentskills.io skill file at
`worker/src/main/resources/skills/issue-triage/SKILL.md` (YAML frontmatter with
`name`/`description`, a "How to select" section, and an `## Owners` table where
each owner carries multiple specialties and preferences). This file is the
single source of truth for the roster and the selection rules.

The spring-ai-community `SkillsTool`
(`org.springaicommunity:spring-ai-agent-utils`, version property
`spring-ai-agent-utils.version`) loads the skill from `classpath:/skills` and is
wired as a **default tool** on the shared `ChatClient` bean in
`ChatClientConfiguration`. The `selectOwner` activity's system prompt tells the
model to use the "issue-triage" skill; its prompt chain is
`prompt().system(...).user(...).call().entity(...)` with no `.tools(...)` call,
so the mocked deep-stub `ChatClient` in tests works as-is. `resolveOwner`
applies a non-blank sanity check on the model's answer; the roster is known only
to the model, through the skill.

**Why:** one skill file is the sole source of truth for the roster and rules,
editable without touching Java, and the LLM pulls it on demand as a tool.

**How to apply:** edit the roster and rules in `SKILL.md`. Type the bean as
`org.springframework.ai.tool.ToolCallback` — `SkillsTool.builder()...build()`
returns a `ToolCallback`. Point `addSkillsResources` at the parent directory
(`classpath:/skills`, overridable via `triage.skills.location`); `SkillsTool`
scans it for `<skill-name>/SKILL.md`.
