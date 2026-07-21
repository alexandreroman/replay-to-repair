# Developer task runner. Run `make` (or `make help`) to
# list the available targets.

.DEFAULT_GOAL := help

# Canonical environment, loaded for every target.
# A missing .env file is not an error.
ifneq (,$(wildcard .env))
include .env
export
endif

# Local overrides, loaded only for dev/run targets so that
# deploy/release targets see the canonical .env values only.
# Sequential include means later assignments win.
DEV_TARGETS := dev app-up test
GOALS := $(or $(MAKECMDGOALS),$(.DEFAULT_GOAL))
ifneq (,$(filter $(DEV_TARGETS),$(GOALS)))
ifneq (,$(wildcard .env.local))
include .env.local
export
endif
endif

# In dev mode the containerized gateway proxies /api/* to the locally-run
# backend through the container-runtime host alias.
#   podman: host.containers.internal · Docker Desktop: host.docker.internal
DEV_BACKEND_HOST ?= host.containers.internal
DEV_BACKEND_PORT ?= 8081

##@ Infra

.PHONY: infra-up
infra-up: ## Start Temporal + gateway in containers
	BACKEND_UPSTREAM=$(DEV_BACKEND_HOST):$(DEV_BACKEND_PORT) \
		docker compose up -d temporal gateway

.PHONY: infra-down
infra-down: ## Stop Temporal + gateway
	docker compose stop temporal gateway

##@ Run

# The worker always runs locally (never containerized) so it can be rebuilt and
# redeployed fast during the demo. It needs ANTHROPIC_API_KEY (from .env.local).
# Both targets run local processes in the foreground; the trap reaps the whole
# process group on Ctrl-C or crash, and any process tearing down takes the rest.

.PHONY: dev
dev: infra-up ## Run the app with backend + worker LOCAL (hot reload)
	@trap 'kill 0' EXIT INT TERM; \
		( cd backend && PORT=$(DEV_BACKEND_PORT) ./mvnw -q spring-boot:run; kill 0 ) & \
		( cd worker && ./mvnw -q spring-boot:run; kill 0 ) & \
		wait

.PHONY: app-up
app-up: ## Run the app with backend CONTAINERIZED; worker stays local (hot reload)
	docker compose up -d --build
	@trap 'kill 0' EXIT INT TERM; \
		( cd worker && ./mvnw -q spring-boot:run; kill 0 ) & \
		wait

.PHONY: app-down
app-down: ## Stop and remove the containers
	docker compose down

.PHONY: app-logs
app-logs: ## Follow logs from the running containers
	docker compose logs -f

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
