---
name: "Owner-selection reason is optional (best-effort)"
description: "The LLM reason is requested but tolerated as absent; TriageStatus carries identical Jackson annotations in both modules"
type: project
---
# Owner-selection reason is optional (best-effort)

The `reason` in the owner-selection LLM reply (`OwnerSelection`,
`OwnerAssignment`, `TriageStatus.assignmentReason`) is best-effort:
requested from the model but not required in code. `OwnerSelector`
normalizes a null or blank reason to `null` and never throws on it. Only a
blank/null `owner` is a malformed reply (throws `IllegalStateException`);
the `none` token yields `Optional.empty()`.

`TriageStatus` exists in both `worker` and `backend` as a shared contract
and must stay identical in shape AND Jackson annotations. Both carry
`@JsonInclude(JsonInclude.Include.NON_NULL)` and
`@JsonIgnoreProperties(ignoreUnknown = true)`.

**Why:** models omit or vary keys; NON_NULL keeps early-step payloads clean
(null `assignedOwner`/`assignmentReason` omitted), and ignoring unknown
fields deserializes defensively across the Temporal DataConverter.

**How to apply:** when touching either `TriageStatus`, mirror the change in
the other module. Do not make the reason required. `OwnerSelection` binds
owner aliases `assignee`/`name` and reason aliases
`justification`/`explanation`/`rationale`.
