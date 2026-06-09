package com.morawski.dev.aidevs.llm;

/**
 * Two-way audio bridge: synthesize speech (TTS) and transcribe speech (STT). Kept as a port so the
 * provider (OpenAI by default) can be swapped without touching the tasks that speak/listen.
 *
 * <p>Needed by the {@code phonecall} task, which converses with the operator entirely through audio.
 * The main LLM stack runs on OpenRouter, which does not expose TTS/Whisper endpoints — hence a
 * dedicated implementation against a provider that does (see {@code OpenAiAudioService}).
 */
public interface AudioService {

    /** Synthesize {@code text} (Polish) into MP3 audio bytes. */
    byte[] textToSpeechMp3(String text);

    /** Transcribe MP3 audio bytes into text (language hint from configuration). */
    String speechToText(byte[] mp3);
}
