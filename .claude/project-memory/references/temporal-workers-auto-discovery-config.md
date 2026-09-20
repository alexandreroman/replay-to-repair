---
name: "Temporal worker auto-discovery needs register-activity-beans"
description: "workflow-packages only scans @WorkflowImpl classes; @ActivityImpl beans register only when register-activity-beans is true"
type: reference
---

# Temporal worker auto-discovery needs register-activity-beans

The worker configures auto-discovery in
`worker/src/main/resources/application.yaml` as:

```yaml
    workers-auto-discovery:
      workflow-packages:
        - io.temporal.demos.replaytorepair.worker.triage
      register-activity-beans: true
```

`workflow-packages` covers `@WorkflowImpl` classes only — they are plain
classes, not Spring beans, so the starter finds them by scanning those
packages. `@ActivityImpl` implementations such as `TriageActivitiesImpl` are
Spring beans and register only when `register-activity-beans` is explicitly
`true`: `WorkersAutoDiscoveryProperties.isRegisterActivityBeans()` returns
false when the flag is unset. Without it the worker polls `issue-triage` with
no activity registered and every workflow execution stalls on the first
Activity task.

`register-nexus-service-beans` stays unset — the project declares no
`@NexusServiceImpl`.

**How to verify:** the `WorkersTemplate` logger prints one line per
registration at startup, so a passing `IssueTriageWorkflowTest` plus
`Registering auto-discovered activity bean 'triageActivitiesImpl' ... task
queue 'issue-triage'` in the log confirms both halves are wired.
