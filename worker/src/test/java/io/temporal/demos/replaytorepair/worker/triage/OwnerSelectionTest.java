package io.temporal.demos.replaytorepair.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;

import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector.OwnerSelection;

/**
 * Plain-Jackson test for {@link OwnerSelection} deserialization: no Spring context and no LLM call,
 * proving the record tolerates unknown fields and common key variants the model may emit.
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

    @Test
    void mapsCommonAliases() throws Exception {
        var json = """
                {"assignee": "alice", "justification": "backend expert"}
                """;

        var selection = mapper.readValue(json, OwnerSelection.class);

        assertThat(selection.owner()).isEqualTo("alice");
        assertThat(selection.reason()).isEqualTo("backend expert");
    }
}
