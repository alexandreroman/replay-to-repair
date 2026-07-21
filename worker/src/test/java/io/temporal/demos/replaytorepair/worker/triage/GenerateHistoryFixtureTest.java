package io.temporal.demos.replaytorepair.worker.triage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regenerates the committed event-history fixture used by {@link SelectOwnerReplayTest}.
 *
 * <p>The fixture is a genuine history produced by the real SDK, standing in for a "production"
 * execution that mis-assigned a security issue. The committed bug in
 * {@link TriageActivitiesImpl#selectOwner} makes the run fully deterministic (it returns
 * {@code "alice"} without ever calling the LLM), so no API key or network is required.
 *
 * <p>Disabled by default; enable it on demand to overwrite the committed fixture.
 */
@Disabled("Regenerate the committed history fixture on demand")
class GenerateHistoryFixtureTest {
    private static final Path FIXTURE_PATH = Path.of("src/test/resources/history/select-owner-failure.json");

    @Test
    void generate() throws Exception {
        var env = TestWorkflowEnvironment.newInstance();
        try {
            var worker = env.newWorker(IssueTriageWorkflow.TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(IssueTriageWorkflowImpl.class);

            var chatClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
            when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                    .thenReturn("carol");
            worker.registerActivitiesImplementations(new TriageActivitiesImpl(chatClient));

            env.start();

            var workflowId = "select-owner-failure-" + UUID.randomUUID();
            var options = WorkflowOptions.newBuilder()
                    .setTaskQueue(IssueTriageWorkflow.TASK_QUEUE)
                    .setWorkflowId(workflowId)
                    .build();
            var workflow = env.getWorkflowClient().newWorkflowStub(IssueTriageWorkflow.class, options);

            var issue = new Issue(
                    "SEC-014",
                    "Login form vulnerable to SQL injection",
                    "User-supplied credentials are concatenated directly into the SQL query, "
                            + "allowing authentication bypass and data exfiltration.");
            workflow.triage(issue);

            var historyJson = env.getWorkflowClient().fetchHistory(workflowId).toJson(true);
            Files.createDirectories(FIXTURE_PATH.getParent());
            Files.writeString(FIXTURE_PATH, historyJson);
        } finally {
            env.close();
        }
    }
}
