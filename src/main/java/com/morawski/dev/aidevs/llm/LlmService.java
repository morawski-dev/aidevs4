package com.morawski.dev.aidevs.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

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

    /**
     * Chat with an explicit system prompt and a per-call model override (OpenRouter model id).
     * Used where a task needs a stronger model than the global default (e.g. a prompt-engineer
     * loop) without changing {@code application.yaml}. A blank model falls back to the default.
     */
    public String chat(String systemPrompt, String userPrompt, String model) {
        var request = chat.prompt().system(systemPrompt).user(userPrompt);
        if (StringUtils.hasText(model)) {
            request = request.options(OpenAiChatOptions.builder().model(model).build());
        }
        return request.call().content();
    }

    /**
     * Send an image plus a text prompt to a vision-capable model and parse the reply into
     * {@code type} (structured output). The image is attached as inline media on the user message;
     * a blank {@code model} falls back to the global default. Used by tasks that must reason over a
     * picture (e.g. {@code electricity}, describing each puzzle tile) — the heavy vision work is
     * delegated here so the task loop only deals with the extracted structure.
     */
    public <T> T extractFromImage(String userPrompt, byte[] image, MimeType mimeType, String model, Class<T> type) {
        var request = chat.prompt()
                .user(u -> u.text(userPrompt).media(mimeType, new ByteArrayResource(image)));
        if (StringUtils.hasText(model)) {
            request = request.options(OpenAiChatOptions.builder().model(model).build());
        }
        return request.call().entity(type);
    }
}
