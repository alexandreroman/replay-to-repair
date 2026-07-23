package io.temporal.demos.replaytorepair.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector.OwnerSelection;

/**
 * Plain-Jackson test for {@link OwnerSelection} deserialization: no Spring context and no LLM call,
 * proving the record tolerates unknown fields, binds every declared key alias the model may emit, and
 * accepts a reply that omits the (best-effort) reason.
 */
class OwnerSelectionTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void ignoresUnknownFields() throws Exception {
        var json = """
                {"owner": "alice", "reason": "backend expert", "confidence": 0.92}
                """;

        var selection = mapper.readValue(json, OwnerSelection.class);

        assertThat(selection.owner()).isEqualTo("alice");
        assertThat(selection.reason()).isEqualTo("backend expert");
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "assignee", "name"})
    void mapsOwnerAliases(String key) throws Exception {
        var json = """
                {"%s": "alice", "reason": "backend expert"}
                """.formatted(key);

        var selection = mapper.readValue(json, OwnerSelection.class);

        assertThat(selection.owner()).isEqualTo("alice");
        assertThat(selection.reason()).isEqualTo("backend expert");
    }

    @ParameterizedTest
    @ValueSource(strings = {"reason", "justification", "explanation", "rationale"})
    void mapsReasonAliases(String key) throws Exception {
        var json = """
                {"owner": "alice", "%s": "backend expert"}
                """.formatted(key);

        var selection = mapper.readValue(json, OwnerSelection.class);

        assertThat(selection.owner()).isEqualTo("alice");
        assertThat(selection.reason()).isEqualTo("backend expert");
    }

    @Test
    void acceptsMissingReason() throws Exception {
        var json = """
                {"owner": "alice"}
                """;

        var selection = mapper.readValue(json, OwnerSelection.class);

        assertThat(selection.owner()).isEqualTo("alice");
        assertThat(selection.reason()).isNull();
    }
}
