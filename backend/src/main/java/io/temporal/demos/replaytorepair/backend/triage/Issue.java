package io.temporal.demos.replaytorepair.backend.triage;

/**
 * A fictional issue submitted for triage.
 *
 * <p>Part of the contract shared with the worker module. The shape must stay identical in both
 * modules so the default Temporal {@code DataConverter} (Jackson JSON) round-trips it unchanged.
 */
public record Issue(String id, String title, String description) {
}
