package io.temporal.demos.replaytorepair.backend.triage;

import java.time.Instant;

/**
 * Snapshot of a triage workflow's progress.
 *
 * <p>Part of the contract shared with the worker module. The shape must stay identical in both
 * modules so the same value is returned by both the live {@code getStatus()} query and the final
 * {@code triage()} result, and round-trips through the default Temporal {@code DataConverter}
 * (Jackson JSON).
 */
public record TriageStatus(
        String issueId,
        String issueTitle,
        Step currentStep,
        String assignedOwner,
        String assignmentReason,
        Instant receivedAt
) {
    public enum Step {
        ISSUE_RECEIVED,
        AI_ANALYSIS,
        OWNER_SELECTED,
        NOTIFYING,
        DONE,
        // FAILED means triage ended in an error (the workflow failed, e.g. no
        // suitable owner). The worker never sets it (the workflow fails via a
        // thrown non-retryable failure); it exists so the backend can label
        // failed executions and the two copies stay identical.
        FAILED
    }
}
