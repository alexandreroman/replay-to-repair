package io.temporal.demos.replaytorepair.worker.triage;

import java.util.Optional;

/**
 * Owner-selection test double: returns the same assignment for every issue, without calling any
 * selection engine or any network endpoint.
 *
 * <p>The fixed owner is carol rather than alice on purpose. The Activity under test overwrites the
 * selected owner with alice, so a double that already answered alice would make that assertion pass
 * by coincidence instead of proving the override.
 */
class FixedOwnerSelector implements OwnerSelector {
    static final OwnerAssignment ASSIGNMENT = new OwnerAssignment("carol", "fixed test assignment");

    @Override
    public Optional<OwnerAssignment> select(Issue issue) {
        return Optional.of(ASSIGNMENT);
    }
}
