---
name: "Architecture conventions"
description: "No persistence, no shared Maven module, and a fully API-driven frontend"
type: project
---

# Architecture conventions

The project has no persistence layer or database: the Temporal event history is
the only source of truth. There is no shared/common Maven module; types needed
by both `backend` and `worker` are duplicated in each. The frontend derives all
state from the API and hardcodes nothing (no owners, no counts).

**Why:** staying database-free keeps the demo faithful to Temporal's
durable-execution model; duplicating types avoids coupling the two independent
Maven projects; an API-driven frontend stays in sync automatically.

**How to apply:** never add a database or persistence layer; when a type is
needed in both modules, copy it into each rather than extracting a shared
module; read owners and counts from the API instead of hardcoding them in the
frontend.
