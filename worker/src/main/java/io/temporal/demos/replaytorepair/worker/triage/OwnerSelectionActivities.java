package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Selects the best owner for an issue by reasoning over each candidate's
 * specialties. This is the Activity that calls out to the LLM — and the one
 * where the demo's bug will later be introduced.
 *
 * <p>It stays a regular Activity (not a local/optimized one) so it retries and
 * heartbeats normally, and so its scheduled input is recorded in the event
 * history and can be extracted for replay-to-repair debugging.
 */
@ActivityInterface
public interface OwnerSelectionActivities {

	@ActivityMethod
	String selectOwner(SelectOwnerInput input);

}
