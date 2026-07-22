package io.temporal.demos.replaytorepair.worker.triage;

import java.util.List;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Exposes a ready-to-use {@link ChatClient} so activities can inject it directly instead of the
 * builder. Spring AI auto-configures the {@link ChatClient.Builder} from the configured model
 * starter.
 *
 * <p>The client exposes the issue-triage skill as a tool: {@link SkillsTool} loads the
 * agentskills.io skill from {@code classpath:/skills} at startup, and the model calls it to learn
 * the owner roster and the selection rules.
 */
@Configuration(proxyBeanMethods = false)
class ChatClientConfiguration {
    @Bean
    ToolCallback skills(
            @Value("${triage.skills.location:classpath:/skills}") List<Resource> skillLocations) {
        return SkillsTool.builder().addSkillsResources(skillLocations).build();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallback skills) {
        return builder.defaultTools(skills).build();
    }
}
