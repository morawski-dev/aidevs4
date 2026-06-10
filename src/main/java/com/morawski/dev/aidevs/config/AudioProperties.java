package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the audio layer (text-to-speech and speech-to-text). The rest of the app talks to
 * LLMs through OpenRouter, but OpenRouter does not serve TTS/Whisper, so the audio layer points at a
 * separate provider (OpenAI by default) with its own key — see {@code OpenAiAudioService}.
 *
 * <p>Used by the {@code phonecall} task, which must speak Polish MP3 to the operator and transcribe the
 * operator's spoken replies.
 *
 * @param apiKey   provider API key (e.g. {@code OPENAI_API_KEY}); the audio layer is skipped/fails fast if blank.
 * @param baseUrl  provider base URL (default {@code https://api.openai.com}).
 * @param ttsModel text-to-speech model id (e.g. {@code gpt-4o-mini-tts} or {@code tts-1}).
 * @param ttsVoice voice name for TTS (e.g. {@code onyx}, {@code alloy}).
 * @param sttModel speech-to-text model id (e.g. {@code gpt-4o-transcribe} or {@code whisper-1}).
 * @param language ISO-639-1 language hint for transcription (e.g. {@code pl}).
 */
@ConfigurationProperties("aidevs.audio")
public record AudioProperties(
        String apiKey,
        String baseUrl,
        String ttsModel,
        String ttsVoice,
        String sttModel,
        String language
) {
}
