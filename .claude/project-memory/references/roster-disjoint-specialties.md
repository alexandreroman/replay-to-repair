---
name: "Roster specialties are mutually disjoint"
description: "Each problem area maps to exactly one owner; alice owns backend/API/relational-database, erin only analytics/ML"
type: project
---

# Roster specialties are mutually disjoint

In the issue-triage roster (`worker/src/main/resources/skills/issue-triage/SKILL.md`,
see [[skills-tool-owner-roster]]) each owner's specialties are mutually disjoint,
so any problem area identifies exactly one owner with no ambiguity. In
particular alice owns the whole backend/API/relational-database domain and
erin's specialties carry no data-storage or database wording, so a backend or
database issue maps unambiguously to alice.

**Why:** the demo assigns issues to a single best owner, so overlapping
specialties would make the "correct" owner ambiguous and blur the
replay-to-repair contrast between the buggy assignment and the fixed one.

**How to apply:** when editing the `## Owners` table, keep every owner's
specialties non-overlapping with the others; never reintroduce data/database
wording under erin while alice owns databases.
