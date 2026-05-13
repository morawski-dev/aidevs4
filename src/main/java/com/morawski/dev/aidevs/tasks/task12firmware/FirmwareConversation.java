package com.morawski.dev.aidevs.tasks.task12firmware;

import com.morawski.dev.aidevs.config.FirmwareProperties;
import io.micrometer.observation.annotation.Observed;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Wraps a tool-calling {@link ChatClient} for the firmware agent: the firmware tools are registered
 * as default tools and Spring AI runs the tool-execution loop automatically (explore → repair
 * settings → run binary → submit). A {@link MessageChatMemoryAdvisor} keeps the conversation across
 * the outer re-prompt rounds driven by {@link FirmwareTask}, so the model remembers what it already
 * discovered. Shell exploration is tool-call heavy, so the window is larger than mailbox's.
 * Pattern follows task09's {@code MailboxConversation}.
 */
@Service
class FirmwareConversation {

    private final ChatClient chat;

    FirmwareConversation(ChatClient.Builder builder, FirmwareTools tools, FirmwareProperties props) {
        var memory = MessageWindowChatMemory.builder().maxMessages(150).build();
        var clientBuilder = builder
                .defaultSystem(SystemPrompt.TEXT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultTools(tools);
        if (StringUtils.hasText(props.model())) {
            clientBuilder.defaultOptions(OpenAiChatOptions.builder().model(props.model()).build());
        }
        this.chat = clientBuilder.build();
    }

    @Observed(name = "firmware.turn")
    String run(String conversationId, String userMessage) {
        return chat.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
