package io.temporal.demos.replaytorepair.worker.triage;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A snapshot of a triage workflow's progress.
 *
 * <p>The workflow keeps a single mutable instance of this record that feeds both the live
 * {@code getStatus} query and the final return value of {@code triage}, so a running workflow and a
 * completed one expose the exact same shape.
 *
 * <p>Part of the contract shared with the {@code backend} module; keep the name, shape and Jackson
 * annotations identical on both sides.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TriageStatus(
        String issueId,
        String issueTitle,
        Step currentStep,
        String assignedOwner,
        String assignmentReason,
        Instant receivedAt) {
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
