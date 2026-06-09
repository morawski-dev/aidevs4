package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task22 {@code phonecall} task — a multi-step <em>voice</em> conversation with
 * a system operator over {@code POST /verify} (the only non-audio step is {@code action:"start"}; every
 * subsequent turn is a base64 MP3 in the {@code audio} field). The goal: learn which of three roads
 * (RD224/RD472/RD820) is passable and get its monitoring disabled → {@code {FLG:...}}.
 *
 * <p>The order of utterances is rigid — a wrong/out-of-order message "burns" the conversation and forces
 * a fresh {@code start} — so the task is driven by a deterministic state machine, with the LLM/STT used
 * only to <em>read</em> the operator (transcribe + classify) and to resolve ambiguous road statuses.
 *
 * @param maxTurns       hard cap on dialog turns in one conversation (safety net against loops).
 * @param maxRestarts    how many times to re-{@code start} the whole scenario after a burned conversation.
 * @param restartPauseMs pause between restart attempts — the operator's suspicion ramps up under rapid
 *                       repeated sessions, so spacing them out keeps the baseline friendly.
 * @param operatorName   the identity we introduce as (the brief: "Tymon Gajewski").
 * @param password       the operators' secret password, given when the operator asks to authenticate.
 * @param classifyModel  OpenRouter model id used as a fallback to classify the operator's intent and the
 *                       passable road when the deterministic keyword pass is ambiguous (negation-heavy
 *                       replies); blank falls back to the global default model.
 */
@ConfigurationProperties("aidevs.phonecall")
public record PhonecallProperties(
        int maxTurns,
        int maxRestarts,
        long restartPauseMs,
        String operatorName,
        String password,
        String classifyModel
) {
}
