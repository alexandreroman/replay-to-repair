package io.temporal.demos.replaytorepair.worker.triage;

import java.time.Instant;

/**
 * A snapshot of a triage workflow's progress.
 *
 * <p>The workflow keeps a single mutable instance of this record that feeds both the live
 * {@code getStatus} query and the final return value of {@code triage}, so a running workflow and a
 * completed one expose the exact same shape.
 *
 * <p>Part of the contract shared with the {@code backend} module; keep the name and shape identical
 * on both sides.
 */
public record TriageStatus(
        String issueId,
        String issueTitle,
        Step currentStep,
        String assignedOwner,
        Instant receivedAt) {

    public enum Step {
        ISSUE_RECEIVED,
        PROFILES_LOADED,
        AI_ANALYSIS,
        OWNER_SELECTED,
        DONE
    }
}
