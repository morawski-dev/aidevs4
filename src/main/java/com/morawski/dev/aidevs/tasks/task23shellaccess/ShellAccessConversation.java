package com.morawski.dev.aidevs.tasks.task23shellaccess;

import com.morawski.dev.aidevs.config.ShellAccessProperties;
import io.micrometer.observation.annotation.Observed;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Wraps a tool-calling {@link ChatClient} for the shellaccess agent: the single shell tool is
 * registered as a default tool and Spring AI runs the tool-execution loop automatically (explore
 * /data → cross-reference logs → print the answer JSON). A {@link MessageChatMemoryAdvisor} keeps the
 * conversation across the outer re-prompt rounds driven by {@link ShellAccessTask}, so the model
 * remembers what it already found. Exploration is tool-call heavy, so the window is generous.
 * Pattern follows task12's {@code FirmwareConversation}.
 */
@Service
class ShellAccessConversation {

    private final ChatClient chat;

    ShellAccessConversation(ChatClient.Builder builder, ShellAccessTools tools, ShellAccessProperties props) {
        var memory = MessageWindowChatMemory.builder().maxMessages(120).build();
        var clientBuilder = builder
                .defaultSystem(SystemPrompt.TEXT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultTools(tools);
        // Set model and/or a completion-token cap; the cap avoids OpenRouter 402s when the default
        // per-request budget (65536) exceeds the account's affordable tokens. Per-turn output is small.
        if (StringUtils.hasText(props.model()) || props.maxTokens() > 0) {
            var options = OpenAiChatOptions.builder();
            if (StringUtils.hasText(props.model())) {
                options.model(props.model());
            }
            if (props.maxTokens() > 0) {
                options.maxTokens(props.maxTokens());
            }
            clientBuilder.defaultOptions(options.build());
        }
        this.chat = clientBuilder.build();
    }

    @Observed(name = "shellaccess.turn")
    String run(String conversationId, String userMessage) {
        return chat.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
