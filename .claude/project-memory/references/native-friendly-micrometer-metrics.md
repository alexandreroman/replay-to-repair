---
name: "Metrics use the plain Micrometer API, never annotations"
description: "Owner-selection timings are recorded through constructor-injected Micrometer calls so the worker stays GraalVM-native-friendly"
type: project
---

# Metrics use the plain Micrometer API, never annotations

Instrumentation in this project is written against the plain Micrometer API:
a `MeterRegistry` is constructor-injected and the meter is called directly.
Annotation-driven metrics are out of scope — no `@Timed`, no `@Observed`, no
Spring AOP, no aspects, proxies, reflection, or extra AOT hints — so the code
compiles to a GraalVM native image without additional configuration.

Owner selection is measured per engine: `LlmOwnerSelector` and
`JevOwnerSelector` each build their own `Timer` in their constructor, against
the shared meter name `triage.owner.selection`, and carry two tags: `engine`
(`llm` / `jev`), which is what the two series are compared across, and
`model`, the id of the model that engine calls. Each engine owns that small
duplication on purpose: there is no wrapper class and no shared constant
holder, so the meter definition stays next to the code it measures and the
engines keep no common dependency beyond the `OwnerSelector` contract. There is
deliberately no `outcome` or `exception` tag: one series per engine and model
is what the engine comparison needs.

The `model` value is read from the object that holds the effective setting —
`ChatModel#getDefaultOptions().getModel()` on the LLM path, after the
auto-configuration has merged the chat and connection properties, and
`TypeSafeClient#defaultModel()` on the Jev path — never from a duplicated
literal or a placeholder default, so the tag cannot disagree with the model
the request carries. A tag value is non-null by contract (`ImmutableTag`
calls `Objects.requireNonNull`), so an id that fails to resolve fails worker
startup instead of mislabelling a series.

In both engines, `select(Issue)` is a one-line
`selectionTimer.record(() -> selectOwner(issue))` over a private
`selectOwner(Issue)` holding the real body. `Timer#record` stops its sample in
a `finally` block, so a selection that throws is timed as well.

The worker serves no application HTTP of its own, so `server.port: 0` leaves
that connector on an ephemeral port nothing connects to, and Actuator listens
on `management.server.port: ${WORKER_MANAGEMENT_PORT:8082}` (8080 is the
gateway, 8081 the dev backend), with
`management.endpoints.web.exposure.include: health,info,metrics,prometheus`.
The meter is read two ways there: `/actuator/metrics/triage.owner.selection`
reports COUNT, TOTAL_TIME and MAX for a quick look with no Prometheus around,
and `/actuator/prometheus` serves a scrape — the Prometheus text format by
default, OpenMetrics through content negotiation
(`Accept: application/openmetrics-text; version=1.0.0`). The worker pom carries
`spring-boot-starter-actuator`, `spring-boot-starter-web` and
`io.micrometer:micrometer-registry-prometheus` (version managed by the Spring
Boot BOM) for this. The Prometheus registry is the `MeterRegistry` the engines
record into, and Actuator's `PrometheusMetricsExportAutoConfiguration` — on
the classpath through the actuator starter — publishes the scrape endpoint as
soon as that registry is present. In the scrape the timer appears as
`triage_owner_selection_seconds_count` / `_sum` / `_max` / `_bucket`, carrying
the `engine` tag as a label.

Quantiles come from histogram buckets, configured by meter name; the map keys
are bracket-quoted because the name contains dots:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        "[triage.owner.selection]": true
      minimum-expected-value:
        "[triage.owner.selection]": 100ms
      maximum-expected-value:
        "[triage.owner.selection]": 30s
```

The bounds decide how many buckets a scrape carries, and `100ms`–`30s` frames
the range an engine call falls in: the HTTP client budget is 20s and the
Activity's start-to-close timeout 30s.

**Why:** the demo restarts the worker live and targets native-image
friendliness; annotation-driven instrumentation pulls in proxying and
reflection that a native build must be taught about, for no gain over a direct
call. A shared meter name with a single distinguishing tag keeps the two
engines' series comparable. Buckets are the form p50/p95 can be recomputed
from: `histogram_quantile(0.95, sum by (le, engine)
(rate(triage_owner_selection_seconds_bucket[5m])))` holds across every worker
scraped, while percentiles computed inside one worker describe that worker
alone and cannot be merged with another's.

**How to apply:** to instrument new code, inject `MeterRegistry` through the
constructor and call the meter directly. A new selection engine registers its
own `Timer` on the `triage.owner.selection` name with its own `engine` tag and
the `model` tag read from its client, and times its work through the same
`select`/`selectOwner` split — it inherits the
histogram configuration, which is keyed by meter name. A new meter that needs
quantiles gets its own `management.metrics.distribution` entries, with bounds
framing its expected range. Tests that build a selector by hand pass a
`SimpleMeterRegistry` and can assert the recorded count.
