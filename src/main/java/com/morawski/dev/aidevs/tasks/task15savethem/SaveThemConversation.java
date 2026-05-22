package com.morawski.dev.aidevs.tasks.task15savethem;

import com.morawski.dev.aidevs.config.SaveThemProperties;
import io.micrometer.observation.annotation.Observed;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Wraps a tool-calling {@link ChatClient} for the savethem recon agent: the recon tools are
 * registered as default tools and Spring AI runs the tool-execution loop automatically
 * (search → call → record). A {@link MessageChatMemoryAdvisor} preserves the conversation across the
 * outer re-prompt rounds driven by {@link SaveThemTask}, so the agent remembers what it discovered.
 * Mirrors task09's {@code MailboxConversation}.
 */
@Service
class SaveThemConversation {

    private final ChatClient chat;

    SaveThemConversation(ChatClient.Builder builder, SaveThemTools tools, SaveThemProperties props) {
        int window = props.memoryWindow() > 0 ? props.memoryWindow() : 120;
        var memory = MessageWindowChatMemory.builder().maxMessages(window).build();
        var clientBuilder = builder
                .defaultSystem(SystemPrompt.TEXT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultTools(tools);
        if (StringUtils.hasText(props.model())) {
            clientBuilder.defaultOptions(OpenAiChatOptions.builder().model(props.model()).build());
        }
        this.chat = clientBuilder.build();
    }

    @Observed(name = "savethem.turn")
    String run(String conversationId, String userMessage) {
        return chat.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
