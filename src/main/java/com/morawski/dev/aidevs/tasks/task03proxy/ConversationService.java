package com.morawski.dev.aidevs.tasks.task03proxy;

import com.morawski.dev.aidevs.config.ProxyProperties;
import io.micrometer.observation.annotation.Observed;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Handles one operator turn: appends the message to the per-session history and lets the
 * tool-calling model reply. Spring AI runs the tool-execution loop automatically and the
 * MessageChatMemoryAdvisor keeps an independent conversation per sessionID.
 */
@Service
public class ConversationService {

    private final ChatClient chat;

    ConversationService(ChatClient.Builder builder, PackageTools tools, ProxyProperties props) {
        var memory = MessageWindowChatMemory.builder().maxMessages(50).build();
        var clientBuilder = builder
                .defaultSystem(SystemPrompt.TEXT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultTools(tools);
        if (StringUtils.hasText(props.model())) {
            clientBuilder.defaultOptions(OpenAiChatOptions.builder().model(props.model()).build());
        }
        this.chat = clientBuilder.build();
    }

    @Observed(name = "proxy.reply")
    public String reply(String sessionId, String msg) {
        return chat.prompt()
                .user(msg)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
    }
}
