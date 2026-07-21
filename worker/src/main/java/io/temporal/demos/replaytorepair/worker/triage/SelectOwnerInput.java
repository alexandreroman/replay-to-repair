package io.temporal.demos.replaytorepair.worker.triage;

import java.util.List;

/**
 * Input to the owner-selection Activity: the issue to triage plus the pool of
 * candidate owners to choose from.
 *
 * <p>This is precisely the payload the replay-to-repair demo extracts from a
 * captured event history to reproduce a production bug in a JUnit test.
 */
public record SelectOwnerInput(Issue issue, List<OwnerProfile> candidates) {
}
