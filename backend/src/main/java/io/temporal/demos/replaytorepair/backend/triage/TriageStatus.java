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
        Instant receivedAt
) {
    public enum Step {
        ISSUE_RECEIVED,
        PROFILES_LOADED,
        AI_ANALYSIS,
        OWNER_SELECTED,
        DONE
    }
}
