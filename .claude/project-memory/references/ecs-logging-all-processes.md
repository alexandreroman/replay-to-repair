---
name: "ECS log format for all processes, always"
description: "Every process (backend and worker) emits ECS structured console logs unconditionally, including local/dev process mode"
type: feedback
---

# ECS log format for all processes, always

All Spring Boot processes emit ECS structured console logs
(`logging.structured.format.console: ecs`) at all times, including local
process mode (`make dev` and `make app-up`). This applies to both the
`backend` and the always-local `worker`. No process emits human-readable
console logs.

**Why:** the demo showcases consistent, machine-parseable structured logs
across every process; a mix of formats undermines that.

**How to apply:** keep `logging.structured.format.console: ecs` in the base
`application.yaml` of each module, unconditional and without profile gating. No
profile, mode switch, or comment disables ECS for the worker or for local runs.
See [[logging-conventions]].
