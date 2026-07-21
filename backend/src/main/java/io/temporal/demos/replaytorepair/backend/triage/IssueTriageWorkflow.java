package io.temporal.demos.replaytorepair.backend.triage;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Minimal typed stub of the triage workflow, declared so this module can start and query it.
 *
 * <p>This module is a Temporal <em>client</em> only: it never registers a worker or an
 * implementation of this interface. The real implementation lives in the worker module. Method
 * names and the workflow type must match the worker's copy exactly.
 */
@WorkflowInterface
public interface IssueTriageWorkflow {
    String TASK_QUEUE = "issue-triage";

    /** Registered workflow type name, used to discover executions via the Visibility API. */
    String WORKFLOW_TYPE = "IssueTriageWorkflow";

    @WorkflowMethod
    TriageStatus triage(Issue issue);

    @QueryMethod
    TriageStatus getStatus();
}
