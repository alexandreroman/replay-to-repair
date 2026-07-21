package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

/**
 * Placeholder implementation of the owner-selection Activity.
 *
 * <p>TODO (business logic, deferred): replace the placeholder below with a real
 * call to the Anthropic model via Spring AI's {@code ChatClient}, prompting it
 * to pick the best-matching owner from {@code input.candidates()} based on the
 * issue content. The demo's bug — an accidentally committed early return that
 * short-circuits the LLM and always returns a fixed name — will be introduced
 * here.
 */
@Component
@ActivityImpl(taskQueues = TriageTaskQueue.NAME)
public class OwnerSelectionActivitiesImpl implements OwnerSelectionActivities {

	@Override
	public String selectOwner(SelectOwnerInput input) {
		return input.candidates().getFirst().name();
	}

}
