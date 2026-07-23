package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TriageActivities {
    /**
     * Returns the selected owner and the reason for the choice. When no roster owner is suitable, this fails with a
     * non-retryable error of type {@code NoSuitableOwner}, which terminates the workflow in error.
     */
    OwnerAssignment selectOwner(Issue issue);

    /**
     * Records the assigned owner on the issue's existing ticket in the ticketing system. Updates the existing ticket;
     * it does not create one.
     */
    void updateTicket(Issue issue, String owner);

    /**
     * Notifies the assigned owner that the issue is theirs. This demo sends no real external notification: it logs the
     * assignment and pauses briefly so the NOTIFYING step stays visible on the dashboard while the frontend polls.
     */
    void notifyAssignment(Issue issue, String owner);
}
