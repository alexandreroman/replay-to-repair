---
name: "Demo design constraints"
description: "Bug lives in the Activity, Workflow runs to completion, neutral owner vocabulary"
type: project
---

# Demo design constraints

The demonstrated bug always lives in the Activity, never in the Workflow. The
Workflow runs sequentially to completion with no early return. Vocabulary is
neutral: fields and statuses say "owner", never "dev".

**Why:** the replay-to-repair demo replays a real event history to reproduce
and fix an Activity bug, so the bug must sit in the Activity while the Workflow
stays deterministic and complete; neutral vocabulary keeps the demo
audience-agnostic.

**How to apply:** put any intentional bug in Activity code; keep the Workflow
linear with no conditional early exits; name fields and statuses with "owner".
