---
name: "List triage workflows by WorkflowType server-side"
description: "Filter issue-triage workflows via the Temporal list API on the default WorkflowType search attribute; server-side, not in the app"
type: feedback
---

# List triage workflows by WorkflowType server-side

Application code lists the issue-triage workflows by filtering server-side with
the Temporal list API: `workflowClient.listExecutions("WorkflowType='<type>'")`.
This List Filter uses `WorkflowType`, a DEFAULT (built-in) search attribute
present on every namespace and the standard visibility store — no custom search
attribute to register, no advanced visibility / Elasticsearch to configure.
Temporal filters on the server; the app does not enumerate the namespace and
filter by type in Java.

**Why:** the built-in `WorkflowType` search attribute lets Temporal filter by
type efficiently with zero extra setup; the concern is only with *custom* search
attributes (which need registration and indexing), not this default one. Keeping
the filter server-side avoids pulling unrelated executions over the wire (still
no database — see [[architecture-conventions]]).

**How to apply:** list triage workflows with a `WorkflowType='...'` List Filter
query via `listExecutions`; rely only on default search attributes and do not
introduce custom search attributes for this.
