---
name: "Owner roster lives twice, once per owner-selection engine"
description: "SKILL.md for the Spring AI engine, triage.roster in application-jev.yaml for the Jev engine; TriageRosterConsistencyTest holds them in step"
type: project
---

# Owner roster lives twice, once per owner-selection engine

The issue-triage owner roster and selection methodology exist in two forms,
one per `OwnerSelector` implementation.

On the Spring AI path (`SpringAiOwnerSelector`, `@Profile("!jev")`), the
roster is known **only model-side**: it lives in a single agentskills.io skill
file at `worker/src/main/resources/skills/issue-triage/SKILL.md` (YAML
frontmatter with `name`/`description`, a "How to select" section, and an
`## Owners` table where each owner carries multiple specialties and
preferences). The spring-ai-community `SkillsTool`
(`org.springaicommunity:spring-ai-agent-utils`, version property
`spring-ai-agent-utils.version`) loads the skill from `classpath:/skills` and
is wired as a **default tool** on the shared `ChatClient` bean in
`ChatClientConfiguration`. The `selectOwner` activity's system prompt tells
the model to use the "issue-triage" skill; its prompt chain is
`prompt().system(...).user(...).call().entity(...)` with no `.tools(...)`
call, so the mocked deep-stub `ChatClient` in tests works as-is.
`resolveOwner` applies a non-blank sanity check on the model's answer; Java
code never sees the roster itself on this path.

On the Jev path (`JevOwnerSelector`, `@Profile("jev")`), the roster is known
**Java-side**: it is configured under `triage.roster` in
`application-jev.yaml`, bound to `TriageRosterProperties`. Jev is a decision
model that calls no tools, so `JevOwnerSelector` builds the decision
question's `criteria` map directly from this configuration (one entry per
owner, formatted from its specialties and preferences, plus a `none` entry)
rather than asking a model to read a skill file.

`TriageRosterConsistencyTest` holds the two copies in step: it parses the
`## Owners` table out of `SKILL.md` and the `triage.roster` list out of
`application-jev.yaml` and asserts they name the same owners in the same
order.

**Why:** each engine can only read the roster the way its model works — a
tool-calling model reads a skill file on demand, a decision model needs its
options handed to it as typed criteria — so one editable source per engine is
unavoidable; the consistency test is what keeps them from drifting apart.

**How to apply:** edit the roster and rules in `SKILL.md` for the Spring AI
engine. Edit `triage.roster` in `application-jev.yaml` for the Jev engine, and
update both together — `TriageRosterConsistencyTest` fails otherwise. Type the
`SkillsTool` bean as `org.springframework.ai.tool.ToolCallback` —
`SkillsTool.builder()...build()` returns a `ToolCallback`. Point
`addSkillsResources` at the parent directory (`classpath:/skills`,
overridable via `triage.skills.location`); `SkillsTool` scans it for
`<skill-name>/SKILL.md`.
