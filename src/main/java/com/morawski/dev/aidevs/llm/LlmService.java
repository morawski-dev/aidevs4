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

    /**
     * Structured output with a per-call model override (OpenRouter model id). Mirrors
     * {@link #chat(String, String, String)} but parses the reply into {@code type} via Spring AI's
     * {@code response_format}. A blank {@code model} falls back to the global default. Used where a
     * task wants a specific model for a structured step (e.g. {@code drone}'s instruction planner)
     * without changing {@code application.yaml}.
     */
    public <T> T extract(String systemPrompt, String userPrompt, String model, Class<T> type) {
        var request = chat.prompt().system(systemPrompt).user(userPrompt);
        if (StringUtils.hasText(model)) {
            request = request.options(OpenAiChatOptions.builder().model(model).build());
        }
        return request.call().entity(type);
    }

    /**
     * Structured output with a per-call model override and an explicit {@code max_tokens} cap. Some
     * providers (OpenRouter) reject a request whose requested completion budget exceeds the account's
     * affordable tokens; capping it keeps small structured extractions affordable. A blank {@code model}
     * falls back to the default; a non-positive {@code maxTokens} leaves the provider default in place.
     */
    public <T> T extract(String systemPrompt, String userPrompt, String model, int maxTokens, Class<T> type) {
        var options = OpenAiChatOptions.builder();
        if (StringUtils.hasText(model)) {
            options.model(model);
        }
        if (maxTokens > 0) {
            options.maxTokens(maxTokens);
        }
        return chat.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(options.build())
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
     * Chat with a per-call model override and an explicit {@code max_tokens} cap, returning plain text.
     * Used where the caller wants free-text output (e.g. JSON it will parse leniently itself, tolerating
     * a model that prepends reasoning) without the strict {@code BeanOutputConverter}, while still
     * capping the budget so OpenRouter doesn't 402 on the default. Blank model / non-positive cap fall back.
     */
    public String chat(String systemPrompt, String userPrompt, String model, int maxTokens) {
        var options = OpenAiChatOptions.builder();
        if (StringUtils.hasText(model)) {
            options.model(model);
        }
        if (maxTokens > 0) {
            options.maxTokens(maxTokens);
        }
        return chat.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(options.build())
                .call()
                .content();
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

    /**
     * Attach a media file (e.g. audio) plus a text prompt to an audio-capable model and return the
     * plain-text reply (transcription). Unlike {@link #extractFromImage} this returns free text rather
     * than structured output — audio-preview models may not support {@code response_format} json schema
     * alongside an audio input. The {@code mimeType} must be one the provider maps to an
     * {@code input_audio} part (use {@code audio/mp3} or {@code audio/wav}). A blank {@code model}
     * falls back to the default.
     */
    public String transcribe(String userPrompt, byte[] media, MimeType mimeType, String model) {
        var request = chat.prompt()
                .user(u -> u.text(userPrompt).media(mimeType, new ByteArrayResource(media)));
        if (StringUtils.hasText(model)) {
            request = request.options(OpenAiChatOptions.builder().model(model).build());
        }
        return request.call().content();
    }
}
