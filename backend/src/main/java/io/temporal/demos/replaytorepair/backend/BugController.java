package io.temporal.demos.replaytorepair.backend;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionMetadata;
import io.temporal.client.WorkflowOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the triage dashboard. Uses Temporal purely as a client: it starts workflows and
 * reads their state through the Visibility API, Queries and Results. There is no database.
 */
@RestController
@RequestMapping(path = "/api/bugs", produces = MediaType.APPLICATION_JSON_VALUE)
class BugController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BugController.class);

    /** Visibility query used to discover every triage execution, open or closed. */
    private static final String LIST_QUERY = "WorkflowType='" + IssueTriageWorkflow.WORKFLOW_TYPE + "'";

    /**
     * Memo key carrying the issue title. Stored at start time so the dashboard can still label a
     * running workflow even while the worker is offline (Query unavailable).
     */
    private static final String MEMO_ISSUE_TITLE = "issueTitle";

    /** Small static, fictional dataset covering distinct areas: backend, security, infra, frontend. */
    private static final List<Issue> ISSUES = List.of(
            new Issue("checkout-500",
                    "Checkout endpoint returns HTTP 500 under load",
                    "The checkout API intermittently fails with HTTP 500 once concurrent requests "
                            + "exceed a few hundred per second."),
            new Issue("login-sqli",
                    "Login form vulnerable to SQL injection",
                    "The login form concatenates the username directly into the SQL query, allowing "
                            + "injection through crafted input."),
            new Issue("pods-oomkilled",
                    "Kubernetes pods OOMKilled after deploy",
                    "Application pods are OOMKilled minutes after each deploy because the memory "
                            + "limit is set below the real heap footprint."),
            new Issue("submit-misaligned",
                    "Submit button misaligned on mobile Safari",
                    "On mobile Safari the submit button overflows its container and overlaps the "
                            + "footer, making it hard to tap."),
            new Issue("jwt-logout",
                    "JWT tokens not invalidated on logout",
                    "Logging out does not revoke the issued JWT, so a stolen token keeps working "
                            + "until it naturally expires."),
            new Issue("tls-renewal",
                    "TLS certificate renewal fails in staging",
                    "The automated TLS certificate renewal job fails in staging, leaving the "
                            + "environment on an expired certificate."));

    private final WorkflowClient workflowClient;

    BugController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * Picks a random issue and starts a new triage workflow without blocking on its completion.
     * Returns immediately once the server has acknowledged the start request.
     */
    @PostMapping(path = "/generate")
    GenerateResponse generate() {
        var issue = ISSUES.get(ThreadLocalRandom.current().nextInt(ISSUES.size()));
        var workflowId = "issue-triage-" + issue.id() + "-" + UUID.randomUUID();

        var options = WorkflowOptions.newBuilder()
                .setTaskQueue(IssueTriageWorkflow.TASK_QUEUE)
                .setWorkflowId(workflowId)
                .setMemo(Map.of(MEMO_ISSUE_TITLE, issue.title()))
                .build();
        var workflow = workflowClient.newWorkflowStub(IssueTriageWorkflow.class, options);

        // Non-blocking start: returns as soon as the server acknowledges, before triage runs.
        WorkflowClient.start(workflow::triage, issue);
        LOGGER.info("Started triage workflow {} for issue {}", workflowId, issue.id());

        return new GenerateResponse(workflowId, issue);
    }

    /**
     * Lists every triage workflow with its current status. Resolves closed workflows from their
     * persisted result (works with no worker running) and open ones from a live Query, falling
     * back to a neutral "waiting for worker" view when the Query cannot be answered.
     */
    @GetMapping
    List<BugView> list() {
        try (Stream<WorkflowExecutionMetadata> executions = workflowClient.listExecutions(LIST_QUERY)) {
            return executions
                    .map(this::resolve)
                    .toList();
        }
    }

    private BugView resolve(WorkflowExecutionMetadata execution) {
        var workflowId = execution.getExecution().getWorkflowId();
        var running = execution.getStatus() == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING;
        var stub = workflowClient.newUntypedWorkflowStub(workflowId);
        try {
            var status = running
                    ? stub.query("getStatus", TriageStatus.class)
                    : stub.getResult(TriageStatus.class);
            return new BugView(workflowId, status.issueTitle(), status.currentStep(),
                    status.assignedOwner(), status.receivedAt());
        } catch (Exception e) {
            // A single unresolvable execution (e.g. worker offline mid-redeploy) must not fail the
            // whole endpoint: keep the dashboard usable by returning a neutral placeholder view.
            LOGGER.warn("Could not resolve triage status for workflow {}: {}", workflowId, e.getMessage());
            return neutralView(execution);
        }
    }

    private BugView neutralView(WorkflowExecutionMetadata execution) {
        var workflowId = execution.getExecution().getWorkflowId();
        return new BugView(workflowId, issueTitleFromMemo(execution),
                TriageStatus.Step.ISSUE_RECEIVED, null, execution.getStartTime());
    }

    private String issueTitleFromMemo(WorkflowExecutionMetadata execution) {
        try {
            var title = execution.getMemo(MEMO_ISSUE_TITLE, String.class, String.class);
            return title != null ? title : "Pending…";
        } catch (Exception e) {
            return "Pending…";
        }
    }

    /** Response of {@code POST /api/bugs/generate}. */
    record GenerateResponse(String workflowId, Issue issue) {
    }

    /** One dashboard card: a stable workflow id plus the flattened {@link TriageStatus} fields. */
    record BugView(
            String workflowId,
            String issueTitle,
            TriageStatus.Step currentStep,
            String assignedOwner,
            Instant receivedAt
    ) {
    }
}
