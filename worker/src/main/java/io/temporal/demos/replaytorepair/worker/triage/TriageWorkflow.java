package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Triages a single incoming issue and assigns it to an owner.
 *
 * <p>The workflow orchestration is deliberately simple and bug-free: it runs
 * sequentially to completion and delegates the actual decision to the
 * owner-selection Activity. The demo's bug lives in the Activity, not here —
 * which is exactly why replaying the workflow alone (reusing recorded Activity
 * results) is not enough to reproduce it.
 */
@WorkflowInterface
public interface TriageWorkflow {

	@WorkflowMethod
	String assign(Issue issue);

}
