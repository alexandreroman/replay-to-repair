package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.List;

/**
 * Sequential, bug-free orchestration. It builds the candidate owner pool and
 * asks the owner-selection Activity to pick one.
 *
 * <p>TODO (business logic, deferred): source the candidate owners from a real
 * fictional dataset instead of the placeholder below.
 */
@WorkflowImpl(taskQueues = TriageTaskQueue.NAME)
public class TriageWorkflowImpl implements TriageWorkflow {

	private final OwnerSelectionActivities activities = Workflow.newActivityStub(
			OwnerSelectionActivities.class,
			ActivityOptions.newBuilder()
					.setStartToCloseTimeout(Duration.ofMinutes(1))
					.build());

	@Override
	public String assign(Issue issue) {
		// Placeholder candidate pool — replaced by the fictional dataset later.
		var candidates = List.of(
				new OwnerProfile("placeholder", List.of("general")));
		return activities.selectOwner(new SelectOwnerInput(issue, candidates));
	}

}
