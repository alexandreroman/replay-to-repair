---
name: "Running workflows in @SpringBootTest against the test server"
description: "Put test-only Temporal settings in a `test` profile overlay, not a second application.yaml, or the base worker-auto-discovery is lost and the workflow call hangs"
type: reference
---

# Running workflows in @SpringBootTest against the test server

To start a workflow from a worker-module `@SpringBootTest` against the in-memory
Temporal test server, put the test-only settings (`test-server.enabled`, the
`.env` import) in a profile-specific `application-test.yaml` and activate the
`test` profile — never in a second `application.yaml`. A test `application.yaml`
sits at the same classpath path as the base one and REPLACES it wholesale,
dropping `spring.temporal.workers-auto-discovery.packages` (so no worker
registers and a synchronous workflow call hangs forever) along with the Spring
AI, skills, and ECS-logging settings. A profile overlay merges on top instead,
so the base config still applies.

The starter starts the `WorkerFactory` with the application context, so no
manual `workerFactory.start()` is needed.

**Gotcha:** a stale `target/test-classes/application.yaml` from an older build
re-introduces the shadowing and makes the workflow call hang — run
`./mvnw -o clean test` after changing the test-config layout.

**How to access:** the relevant starter behavior lives in its sources at
`~/.m2/repository/io/temporal/temporal-spring-boot-autoconfigure/<ver>/*-sources.jar`
(`RootNamespaceAutoConfiguration`, `WorkersPresentCondition`).
