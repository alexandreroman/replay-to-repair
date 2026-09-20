package io.temporal.demos.replaytorepair.worker.triage;

import java.util.Optional;

/** Picks the owner best suited to an issue. */
public interface OwnerSelector {
    /**
     * Selects the owner best suited to the given issue.
     *
     * @param issue the issue to triage
     * @return the chosen owner and the reason for the choice, or empty when no owner is suitable
     * @throws IllegalStateException when the selection engine returns a malformed answer
     */
    Optional<OwnerAssignment> select(Issue issue);
}
