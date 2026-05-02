package com.morawski.dev.aidevs.tasks.task09mailbox;

import com.morawski.dev.aidevs.config.MailboxProperties;
import io.micrometer.observation.annotation.Observed;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Wraps a tool-calling {@link ChatClient} for the mailbox agent: the mailbox tools are registered
 * as default tools and Spring AI runs the tool-execution loop automatically (search → read →
 * submit). A {@link MessageChatMemoryAdvisor} keeps the conversation across the outer re-prompt
 * rounds driven by {@link MailboxTask}, so the model remembers what it already searched and read.
 * Pattern follows task03's {@code ConversationService}.
 */
@Service
class MailboxConversation {

    private final ChatClient chat;

    MailboxConversation(ChatClient.Builder builder, MailboxTools tools, MailboxProperties props) {
        var memory = MessageWindowChatMemory.builder().maxMessages(80).build();
        var clientBuilder = builder
                .defaultSystem(SystemPrompt.TEXT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultTools(tools);
        if (StringUtils.hasText(props.model())) {
            clientBuilder.defaultOptions(OpenAiChatOptions.builder().model(props.model()).build());
        }
        this.chat = clientBuilder.build();
    }

    @Observed(name = "mailbox.turn")
    String run(String conversationId, String userMessage) {
        return chat.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
