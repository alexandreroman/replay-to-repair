package io.temporal.demos.replaytorepair.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

/**
 * Runs the full {@link IssueTriageWorkflow} end to end against the in-memory Temporal test server
 * that the temporal-spring-boot-starter starts under the {@code test} profile (see {@code
 * src/test/resources/application-test.yaml}), triaging a backend issue — alice's domain in the
 * triage roster — and asserting the workflow assigns it to {@code alice} and completes.
 *
 * <p>The {@code test} profile overlays the module's main config, so the worker-auto-discovery
 * packages come from {@code application.yaml} and the starter registers the {@code
 * @WorkflowImpl}/{@code @ActivityImpl} beans on a worker it starts with the application context.
 */
@SpringBootTest
@ActiveProfiles("test")
class IssueTriageWorkflowTest {
    // The starter wires this WorkflowClient to the in-memory test server the worker polls.
    @Autowired
    private WorkflowClient workflowClient;

    @Test
    void triagesBackendIssueToAlice() {
        var issue = new Issue(
                "API-1",
                "Checkout endpoint returns HTTP 500 under load",
                "The POST /api/checkout endpoint throws NullPointerException once concurrent requests "
                        + "spike, and the order service returns 500 to the client");
        var options = WorkflowOptions.newBuilder()
                .setTaskQueue(IssueTriageWorkflow.TASK_QUEUE)
                .setWorkflowId("issue-triage-test-" + issue.id())
                .build();
        var workflow = workflowClient.newWorkflowStub(IssueTriageWorkflow.class, options);

        // Synchronous call: blocks until the workflow completes. notifyAssignment runs a real ~2s
        // Thread.sleep, so this test takes a couple of seconds.
        TriageStatus result = workflow.triage(issue);

        assertThat(result.assignedOwner()).isEqualTo("alice");
        assertThat(result.assignmentReason()).isNotBlank();
        assertThat(result.currentStep()).isEqualTo(TriageStatus.Step.DONE);
    }
}
