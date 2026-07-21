---
name: "Logging conventions"
description: "Use the SLF4J 2.x fluent API with addKeyValue context; in Temporal workflow code get the logger via Workflow.getLogger"
type: feedback
---

# Logging conventions

Log with the SLF4J 2.x fluent API and carry the business context in key-values,
not interpolated into the message string:

```java
LOGGER.atInfo()
        .addKeyValue("issueId", issue.id())
        .addKeyValue("owner", owner)
        .log("Owner selected");
```

Use `.addKeyValue(key, value)` for context, `.setCause(throwable)` for
exceptions, and a short, constant human-readable `.log("...")` message. Use
`atInfo()`, `atWarn()`, `atDebug()` as appropriate.

In Temporal **workflow** code, obtain the logger via
`io.temporal.workflow.Workflow.getLogger(TheWorkflowImpl.class)` (a replay-aware
logger), never `LoggerFactory.getLogger`. The fluent API is replay-safe on it:
`ReplayAwareLogger` overrides the `isXxxEnabled()` checks and the terminal
`log(...)` delegates to the replay-guarded methods, so no duplicate output on
replay. Everywhere else (activities, REST controllers, tooling) use
`LoggerFactory.getLogger(TheClass.class)`.

**Why:** key-value context is greppable and machine-parseable by structured log
backends; `Workflow.getLogger` prevents duplicate log lines during workflow
replay.

**How to apply:** keep the `LOGGER` field as the first member of the class;
prefer key-values over string interpolation for identifiers and values. Note
that rendering the key-values in the console requires `%kvp` in the Logback
pattern (default Spring Boot pattern omits them). See [[code-style-conventions]].
