# Developer task runner. Run `make` (or `make help`) to
# list the available targets.

.DEFAULT_GOAL := help

# Canonical environment, loaded for every target.
# A missing .env file is not an error.
ifneq (,$(wildcard .env))
include .env
export
endif

# Container tooling, auto-detected with no user action: Docker is preferred,
# Podman is the fallback. Override with `make COMPOSE="..."` if needed.
COMPOSE ?= $(shell \
	if docker compose version >/dev/null 2>&1; then echo "docker compose"; \
	elif podman compose version >/dev/null 2>&1; then echo "podman compose"; \
	elif command -v podman-compose >/dev/null 2>&1; then echo "podman-compose"; \
	else echo "docker compose"; fi)

# In dev mode the containerized gateway proxies /api/* to the locally-run
# backend through the container-runtime host alias, selected from the detected
# tooling: Docker (default) uses host.docker.internal, mapped via a host-gateway
# extra_hosts entry; Podman (fallback) provides host.containers.internal
# natively and skips the host-gateway entry, which breaks Podman rootless.
ifneq (,$(findstring podman,$(COMPOSE)))
DEV_BACKEND_HOST ?= host.containers.internal
# Harmless placeholder; real host access uses host.containers.internal
# (avoids Podman rootless host-gateway error).
GATEWAY_HOST_MAPPING ?= podman-host-gateway-unused.invalid:127.0.0.1
else
DEV_BACKEND_HOST ?= host.docker.internal
GATEWAY_HOST_MAPPING ?= host.docker.internal:host-gateway
endif
DEV_BACKEND_PORT ?= 8081

# Port for the URLs printed after startup. Conditional so a value imported from
# .env (above) wins; this is only the fallback for a plain checkout.
GATEWAY_PORT ?= 8080

# Event-history fixture that IssueTriageWorkflowReplayTest reads and that
# `make capture-history` writes.
REPLAY_FIXTURE := worker/src/test/resources/history/issue-triage.json

# Make the gateway host mapping visible to $(COMPOSE) in every target.
export GATEWAY_HOST_MAPPING

##@ Infra

.PHONY: infra-up
infra-up: ## Start Temporal + gateway in containers
	BACKEND_UPSTREAM=$(DEV_BACKEND_HOST):$(DEV_BACKEND_PORT) \
		$(COMPOSE) up -d temporal gateway

.PHONY: infra-down
infra-down: ## Stop Temporal + gateway
	$(COMPOSE) stop temporal gateway

# Print the demo's useful URLs. The gateway serves the dashboard and proxies
# /api/* to the backend and /temporal to the Temporal Web UI.
define show_urls
	@echo ""
	@echo "The app is up."
	@echo "Open:"
	@echo "  Dashboard         http://localhost:$(GATEWAY_PORT)"
	@echo "  Temporal Web UI   http://localhost:$(GATEWAY_PORT)/temporal"
endef

##@ Run

# The worker always runs locally (never containerized) so it can be rebuilt and
# redeployed fast during the demo. It needs ANTHROPIC_API_KEY (from .env).
# Both targets run local processes in the foreground; the trap reaps the whole
# process group on Ctrl-C or crash, and any process tearing down takes the rest.

.PHONY: dev
dev: infra-up ## Run the app with backend + worker LOCAL (hot reload)
	$(show_urls)
	@trap 'kill 0' EXIT INT TERM; \
		( cd backend && PORT=$(DEV_BACKEND_PORT) ./mvnw -q spring-boot:run; kill 0 ) & \
		( cd worker && ./mvnw -q spring-boot:run; kill 0 ) & \
		wait

.PHONY: app-up
app-up: ## Run the app with backend CONTAINERIZED; worker stays local (hot reload)
	$(COMPOSE) up -d --build
	$(show_urls)
	@trap 'kill 0' EXIT INT TERM; \
		( cd worker && ./mvnw -q spring-boot:run; kill 0 ) & \
		wait

.PHONY: app-down
app-down: ## Stop and remove the containers
	$(COMPOSE) down

##@ Workspace

# Casper workspace setup hook: give each parallel workspace (Git worktree) its
# own host ports so several worktrees can run the demo at once without
# colliding. Runs once at workspace creation. No-op in a plain checkout, where
# CASPER_PORT is unset and the compose/Makefile defaults (8080/7233/8081)
# apply unchanged.
#
# Ports are derived from the Casper-injected base CASPER_PORT (Casper reserves
# CASPER_PORT..CASPER_PORT+10 for us):
#   +0  gateway (browser entry point)
#   +1  Temporal gRPC (local worker + local backend connect here)
#   +2  local backend in dev mode
# The Temporal Web UI needs no port of its own: it rides the gateway port (+0)
# at /temporal.
# All variables are $$-escaped so the shell — not Make — expands them.
.PHONY: worktree-init
worktree-init: ## Remap host ports for a Casper worktree and pre-compile both modules
	@if [ -n "$$CASPER_PORT" ]; then \
		env_file=".env"; \
		gateway_port=$$CASPER_PORT; \
		temporal_grpc_port=$$((CASPER_PORT + 1)); \
		dev_backend_port=$$((CASPER_PORT + 2)); \
		if [ -f "$$env_file" ]; then \
			grep -vE '^(GATEWAY_PORT|TEMPORAL_GRPC_PORT|DEV_BACKEND_PORT|TEMPORAL_ADDRESS)=' "$$env_file" > "$$env_file.casper.tmp" || true; \
			mv "$$env_file.casper.tmp" "$$env_file"; \
		fi; \
		{ \
			echo "# --- Casper workspace port remap (generated from base $$CASPER_PORT) ---"; \
			echo "GATEWAY_PORT=$$gateway_port"; \
			echo "TEMPORAL_GRPC_PORT=$$temporal_grpc_port"; \
			echo "DEV_BACKEND_PORT=$$dev_backend_port"; \
			echo "TEMPORAL_ADDRESS=localhost:$$temporal_grpc_port"; \
		} >> "$$env_file"; \
		echo "Casper: this worktree uses gateway=$$gateway_port temporal-grpc=$$temporal_grpc_port backend-dev=$$dev_backend_port"; \
	fi
	cd backend && ./mvnw -B -DskipTests compile
	cd worker && ./mvnw -B -DskipTests compile

##@ Replay

# Capture the most recent IssueTriageWorkflow's event history into the replay
# fixture that IssueTriageWorkflowReplayTest reads. Needs the Temporal CLI and
# jq, with Temporal reachable. The Temporal CLI honors TEMPORAL_ADDRESS (set in
# a Casper worktree; defaults to localhost:7233 in a plain checkout). The list
# defaults to newest-first (StartTime descending), so --limit 1 with .[0] yields
# the most recent execution; the demo's visibility store rejects ORDER BY.
.PHONY: capture-history
capture-history: ## Capture the last triage workflow's event history into the replay fixture
	@command -v temporal >/dev/null 2>&1 || { echo "temporal CLI not found (see https://docs.temporal.io/cli)"; exit 1; }
	@command -v jq >/dev/null 2>&1 || { echo "jq not found (install jq)"; exit 1; }
	@wid=$$(temporal workflow list \
			--query 'WorkflowType = "IssueTriageWorkflow"' \
			--limit 1 --output json | jq -r '.[0].execution.workflowId // empty'); \
		if [ -z "$$wid" ]; then \
			echo "No IssueTriageWorkflow execution found — trigger one from the dashboard first."; \
			exit 1; \
		fi; \
		echo "Capturing event history of workflow $$wid"; \
		temporal workflow show --workflow-id "$$wid" --output json > $(REPLAY_FIXTURE); \
		echo "Wrote $(REPLAY_FIXTURE)"

##@ Quality

.PHONY: test
test: ## Run the test suite for both Maven modules
	cd backend && ./mvnw -B test
	cd worker && ./mvnw -B test

##@ Build

.PHONY: build
build: ## Build the production JARs for both modules
	cd backend && ./mvnw -B -DskipTests package
	cd worker && ./mvnw -B -DskipTests package

##@ Helpers

.PHONY: help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make \033[36m<target>\033[0m\n"} \
		/^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } \
		/^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) }' $(firstword $(MAKEFILE_LIST))
