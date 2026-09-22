package io.temporal.demos.replaytorepair.worker.triage.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards the two copies of the owner roster against drift: the Markdown table in the issue-triage
 * skill, which only the LLM engine's model reads, and the configured roster the Jev engine
 * turns into its question criteria. Specialties and preferences are the actual routing signal for
 * both engines, so the whole roster is compared, not just owner names.
 */
class TriageRosterConsistencyTest {
    @Test
    void skillAndConfiguredRosterListTheSameOwners() throws IOException {
        assertThat(configuredOwnerRows()).containsExactlyElementsOf(skillOwnerRows());
    }

    /** One roster entry as read from either source, trimmed for a like-for-like comparison. */
    private record OwnerRow(String name, List<String> specialties, List<String> preferences) {
    }

    private static List<OwnerRow> skillOwnerRows() throws IOException {
        var markdown = new ClassPathResource("skills/issue-triage/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8);
        var rows = new ArrayList<OwnerRow>();
        var inOwners = false;
        for (var line : markdown.lines().toList()) {
            if (line.startsWith("## ")) {
                inOwners = line.startsWith("## Owners");
                continue;
            }
            // Skip the header row and the |---|---| separator; keep the data rows.
            if (inOwners && line.startsWith("|") && !line.contains("---") && !line.contains("Owner |")) {
                var cells = line.split("\\|");
                rows.add(new OwnerRow(cells[1].trim(), splitCell(cells[2]), splitCell(cells[3])));
            }
        }
        return rows;
    }

    /** Splits a Markdown table cell holding a comma-separated list into trimmed elements. */
    private static List<String> splitCell(String cell) {
        return Arrays.stream(cell.split(",")).map(String::trim).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<OwnerRow> configuredOwnerRows() throws IOException {
        try (InputStream yaml = new ClassPathResource("application-jev.yaml").getInputStream()) {
            var root = (Map<String, Object>) new Yaml().load(yaml);
            var triage = (Map<String, Object>) root.get("triage");
            var roster = (Map<String, Object>) triage.get("roster");
            var owners = (List<Map<String, Object>>) roster.get("owners");
            return owners.stream()
                    .map(owner -> new OwnerRow(
                            (String) owner.get("name"),
                            (List<String>) owner.get("specialties"),
                            (List<String>) owner.get("preferences")))
                    .toList();
        }
    }
}
