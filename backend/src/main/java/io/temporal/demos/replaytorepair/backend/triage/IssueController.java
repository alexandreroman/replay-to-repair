package io.temporal.demos.replaytorepair.backend.triage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * REST API for the triage dashboard. Uses Temporal purely as a client: it starts workflows, reads
 * their state through Queries and Results, and lists executions filtered by workflow type on the
 * server. There is no database.
 */
@RestController
@RequestMapping(path = "/api/v1/issues", produces = MediaType.APPLICATION_JSON_VALUE)
class IssueController {
    private static final Logger LOGGER = LoggerFactory.getLogger(IssueController.class);

    /**
     * Memo key carrying the issue title. Stored at start time so the dashboard can still label a
     * running workflow even while the worker is offline (Query unavailable).
     */
    private static final String MEMO_ISSUE_TITLE = "issueTitle";

    /**
     * Memo key carrying the issue description. Stored at start time so the dashboard can still show
     * the description even while the worker is offline (Query unavailable).
     */
    private static final String MEMO_ISSUE_DESCRIPTION = "issueDescription";

    private final WorkflowClient workflowClient;
    private final IssueGenerator issueGenerator;

    IssueController(WorkflowClient workflowClient, IssueGenerator issueGenerator) {
        this.workflowClient = workflowClient;
        this.issueGenerator = issueGenerator;
    }

    /**
     * Picks a random issue and starts a new triage workflow without blocking on its completion.
     * Returns immediately once the server has acknowledged the start request.
     */
    @PostMapping(path = "/generate")
    GenerateResponse generate() {
        var issue = issueGenerator.next();
        // Short random suffix: unique-enough to avoid id clashes across demo runs while staying readable.
        var suffix = UUID.randomUUID().toString().substring(0, 6);
        var workflowId = "issue-triage-" + issue.id() + "-" + suffix;

        var options = WorkflowOptions.newBuilder()
                .setTaskQueue(IssueTriageWorkflow.TASK_QUEUE)
                .setWorkflowId(workflowId)
                .setMemo(Map.of(MEMO_ISSUE_TITLE, issue.title(), MEMO_ISSUE_DESCRIPTION, issue.description()))
                .build();
        var workflow = workflowClient.newWorkflowStub(IssueTriageWorkflow.class, options);

        // Non-blocking start: returns as soon as the server acknowledges, before triage runs.
        WorkflowClient.start(workflow::triage, issue);
        LOGGER.atInfo()
                .addKeyValue("workflowId", workflowId)
                .addKeyValue("issueId", issue.id())
                .addKeyValue("issueTitle", issue.title())
                .log("triage.workflow.started");

        return new GenerateResponse(workflowId, issue);
    }

    /**
     * Lists every triage workflow with its current status. Resolves closed workflows from their
     * persisted result (works with no worker running) and open ones from a live Query, falling
     * back to a neutral "waiting for worker" view when the Query cannot be answered.
     */
    @GetMapping
    List<IssueView> list() {
        // Temporal filters by workflow type server-side via the default WorkflowType search attribute.
        try (Stream<WorkflowExecutionMetadata> executions =
                workflowClient.listExecutions("WorkflowType='" + IssueTriageWorkflow.WORKFLOW_TYPE + "'")) {
            return executions
                    .map(this::resolve)
                    .toList();
        }
    }

    private IssueView resolve(WorkflowExecutionMetadata execution) {
        var workflowId = execution.getExecution().getWorkflowId();
        var status = execution.getStatus();

        // A terminal-but-not-completed execution (FAILED / TERMINATED / TIMED_OUT / CANCELED /
        // CONTINUED_AS_NEW) is a failed triage: it has no result to read and no live worker to
        // query. Surface it as FAILED, distinct from an execution that is simply not resolved yet.
        var running = status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING;
        var completed = status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED;
        if (!running && !completed) {
            return failedView(execution, workflowId);
        }

        // Running executions expose their state through a live Query; completed ones through their
        // persisted result (readable even with no worker running).
        var stub = workflowClient.newUntypedWorkflowStub(workflowId);
        try {
            var triage = running
                    ? stub.query("getStatus", TriageStatus.class)
                    : stub.getResult(TriageStatus.class);
            return new IssueView(workflowId, triage.issueTitle(), issueDescriptionFromMemo(execution),
                    triage.currentStep(), triage.assignedOwner(), triage.receivedAt(), workflowUrl(execution));
        } catch (Exception e) {
            // A single unresolvable execution (e.g. worker offline mid-redeploy) must not fail the
            // whole endpoint: keep the dashboard usable by returning a neutral placeholder view.
            LOGGER.atWarn()
                    .addKeyValue("workflowId", workflowId)
                    .setCause(e)
                    .log("triage.status.unresolved");
            return neutralView(execution, workflowId);
        }
    }

    /** Neutral placeholder for a running or completed execution whose status is not resolvable yet. */
    private IssueView neutralView(WorkflowExecutionMetadata execution, String workflowId) {
        return new IssueView(workflowId, issueTitleFromMemo(execution), issueDescriptionFromMemo(execution),
                TriageStatus.Step.ISSUE_RECEIVED, null, execution.getStartTime(), workflowUrl(execution));
    }

    /** View for a terminal non-completed execution: a triage that ended in failure. */
    private IssueView failedView(WorkflowExecutionMetadata execution, String workflowId) {
        return new IssueView(workflowId, issueTitleFromMemo(execution), issueDescriptionFromMemo(execution),
                TriageStatus.Step.FAILED, null, execution.getStartTime(), workflowUrl(execution));
    }

    /** Relative Temporal Web UI deep-link to the workflow run, proxied same-origin under {@code /temporal}. */
    private String workflowUrl(WorkflowExecutionMetadata execution) {
        var namespace = workflowClient.getOptions().getNamespace();
        var workflowId = execution.getExecution().getWorkflowId();
        var runId = execution.getExecution().getRunId();
        return "/temporal/namespaces/" + namespace + "/workflows/" + workflowId + "/" + runId + "/history";
    }

    private String issueTitleFromMemo(WorkflowExecutionMetadata execution) {
        try {
            var title = execution.getMemo(MEMO_ISSUE_TITLE, String.class, String.class);
            return title != null ? title : "Pending…";
        } catch (Exception e) {
            return "Pending…";
        }
    }

    private String issueDescriptionFromMemo(WorkflowExecutionMetadata execution) {
        try {
            return execution.getMemo(MEMO_ISSUE_DESCRIPTION, String.class, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Response of {@code POST /api/v1/issues/generate}. */
    record GenerateResponse(String workflowId, Issue issue) {
    }

    /**
     * One dashboard card: a stable workflow id, the flattened {@link TriageStatus} fields, and a
     * relative deep-link to the workflow run in the Temporal Web UI.
     */
    record IssueView(
            String workflowId,
            String issueTitle,
            String issueDescription,
            TriageStatus.Step currentStep,
            String assignedOwner,
            Instant receivedAt,
            String workflowUrl
    ) {
    }
}
