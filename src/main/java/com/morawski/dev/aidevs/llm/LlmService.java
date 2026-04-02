package com.morawski.dev.aidevs.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

    private final ChatClient chat;

    public LlmService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public <T> T extract(String prompt, Class<T> type) {
        return chat.prompt(prompt).call().entity(type);
    }

    public <T> T extract(String systemPrompt, String userPrompt, Class<T> type) {
        return chat.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(type);
    }

    public String chat(String prompt) {
        return chat.prompt(prompt).call().content();
    }
}
