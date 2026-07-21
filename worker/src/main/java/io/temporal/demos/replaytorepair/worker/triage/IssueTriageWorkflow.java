package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Triages a single {@link Issue}: loads the candidate owners, asks the AI agent to pick the best
 * match, and records the assignment.
 *
 * <p>The workflow runs sequentially to completion with no early return. Callers get non-blocking
 * behavior by starting it with {@code WorkflowClient.start(...)}, never through a shortcut inside
 * the workflow itself.
 */
@WorkflowInterface
public interface IssueTriageWorkflow {

    /** Task queue the worker polls for this workflow and its activities. */
    String TASK_QUEUE = "issue-triage";

    @WorkflowMethod
    TriageStatus triage(Issue issue);

    @QueryMethod
    TriageStatus getStatus();
}
