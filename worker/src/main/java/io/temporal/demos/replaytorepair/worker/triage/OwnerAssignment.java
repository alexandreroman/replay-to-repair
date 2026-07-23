package io.temporal.demos.replaytorepair.worker.triage;

/** Owner-selection result: the chosen owner and the human-readable reason for the choice. */
public record OwnerAssignment(String owner, String reason) {
}
