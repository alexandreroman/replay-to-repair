---
name: "Docker and Podman compatibility"
description: "Container setup runs on Docker (preferred) and Podman with no user intervention"
type: project
---

# Docker and Podman compatibility

The container setup targets **Docker by default** and stays Podman-compatible
with no user action. The `Makefile` auto-detects the container tooling into a
`COMPOSE` variable (`docker compose`, else `podman compose`, else
`podman-compose`) and every recipe invokes `$(COMPOSE)` instead of a hardcoded
`docker compose`.

Only one container ever reaches back to the host: in `make dev` the Caddy
gateway proxies `/api/*` to the locally-run backend. The host alias is
runtime-derived: `host.docker.internal` on Docker, `host.containers.internal`
on Podman. The gateway service in `compose.yaml` carries
`extra_hosts: ["${GATEWAY_HOST_MAPPING:-host.docker.internal:host-gateway}"]`.

- On Docker the default maps `host.docker.internal` to the host gateway —
  required on Docker Engine / Linux, harmless on Docker Desktop.
- On Podman the Makefile overrides `GATEWAY_HOST_MAPPING` with a harmless
  static placeholder entry, because Podman rootless cannot resolve the
  `host-gateway` magic value (it errors with "host containers internal IP
  address is empty") and exposes `host.containers.internal` natively instead.

**Why:** the demo is presented on Docker but must run on Podman machines too,
without asking the presenter to edit files or set environment variables.

**How to apply:** keep host access flowing through the gateway only; when
changing the compose/gateway wiring, preserve the runtime-derived
`DEV_BACKEND_HOST` and the `GATEWAY_HOST_MAPPING` override, and never hardcode
`docker compose` — use `$(COMPOSE)`. Both variables use `?=`, so an explicit
override still wins.
