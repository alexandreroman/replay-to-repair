package io.temporal.demos.replaytorepair.worker.triage;

/**
 * An incoming issue to triage.
 *
 * <p>Part of the contract shared with the {@code backend} module. The two modules deliberately
 * duplicate this record instead of sharing a common Maven module, so its name and shape must stay
 * identical on both sides.
 */
public record Issue(String id, String title, String description) {
}
