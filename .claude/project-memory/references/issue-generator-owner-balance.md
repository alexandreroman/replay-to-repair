---
name: "IssueGenerator dataset is balanced across the five owners"
description: "Per-owner issue counts are kept roughly equal (~6 each incl. erin analytics/ML); next() stays a uniform random pick"
type: project
---

# IssueGenerator dataset is balanced across the five owners

The backend `IssueGenerator` dataset
(`backend/src/main/java/io/temporal/demos/replaytorepair/backend/triage/IssueGenerator.java`)
holds roughly equal numbers of issues per triage owner — about six each for
alice, bob, carol, dave, and erin — and covers all five specialties including
analytics/ML issues that map to erin. `next()` is a plain uniform random pick
over the flat list, so balancing lives entirely in the per-owner issue counts,
not in the sampling logic. See [[roster-disjoint-specialties]].

**Why:** with a uniform random pick, a dataset skewed toward one owner
over-assigns to that owner; alice's backend/API/relational-database domain acts
as a catch-all, so an unbalanced dataset sends a majority of generated issues to
her once triage is working correctly.

**How to apply:** when adding or removing issues, keep the per-owner counts
roughly even and make sure each new issue maps unambiguously to exactly one
owner via the disjoint roster specialties. Keep `next()` a uniform pick — do not
switch to stratified sampling. Never add a category field to the shared `Issue`
record; the intended owner must not leak to the triage LLM.
