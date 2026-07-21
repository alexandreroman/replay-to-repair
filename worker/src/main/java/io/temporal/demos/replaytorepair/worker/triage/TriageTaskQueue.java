package io.temporal.demos.replaytorepair.worker.triage;

/**
 * Task queue on which the triage workflow and its activities are registered.
 * The backend (client) and this worker must agree on this exact name.
 */
public final class TriageTaskQueue {

	public static final String NAME = "triage-task-queue";

	private TriageTaskQueue() {
	}

}
