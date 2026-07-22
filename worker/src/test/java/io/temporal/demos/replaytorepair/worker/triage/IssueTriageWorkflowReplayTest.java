package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.testing.WorkflowReplayer;
import org.junit.jupiter.api.Test;

/**
 * Replays the recorded production Workflow Execution against the current {@link IssueTriageWorkflowImpl} and passes
 * when the workflow replays with no non-determinism.
 *
 * <p>During replay the SDK feeds the recorded Activity results back into the workflow, so Activity code is not
 * re-executed. This test therefore guards workflow determinism rather than exercising the owner-selection Activity.
 *
 * <p>This test runs as part of the suite. If the Workflow changes in a way that is incompatible with the committed
 * event history, refresh the fixture with {@code make capture-history} (see the README).
 *
 * <p>To walk through the real production execution, run this test in the IDE debugger with breakpoints set in the
 * workflow code.
 */
class IssueTriageWorkflowReplayTest {
    @Test
    void replaysProductionHistoryWithoutNonDeterminism() throws Exception {
        WorkflowReplayer.replayWorkflowExecutionFromResource(
                "history/issue-triage.json", IssueTriageWorkflowImpl.class);
    }
}
