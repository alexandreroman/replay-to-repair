package io.temporal.demos.replaytorepair.worker.triage;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a ready-to-use {@link ChatClient} so activities can inject it directly instead of the
 * builder. Spring AI auto-configures the {@link ChatClient.Builder} from the Anthropic starter.
 */
@Configuration(proxyBeanMethods = false)
class ChatClientConfiguration {
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
