package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TriageActivities {
    /**
     * Returns the selected owner name for the issue. When no roster owner is suitable, this fails with a
     * non-retryable error of type {@code NoSuitableOwner}, which terminates the workflow in error.
     */
    String selectOwner(Issue issue);

    void notifyAssignment(Issue issue, String owner);
}
