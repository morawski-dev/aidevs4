package com.morawski.dev.aidevs.tasks.task22phonecall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.PhonecallProperties;
import com.morawski.dev.aidevs.llm.AudioService;
import com.morawski.dev.aidevs.llm.LlmService;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * S05E02 ({@code phonecall}) — hold a multi-step <em>voice</em> conversation with a system operator to learn
 * which of three roads (RD224/RD472/RD820) is passable for moving people to Zion, then talk the operator
 * into disabling monitoring on that road. When the road is successfully unblocked, the Hub returns the flag.
 *
 * <p>After {@code action:"start"} every turn is a base64 MP3 in the {@code audio} field, and the operator
 * answers with audio too. The order of our utterances is rigid — a wrong/out-of-order message "burns" the
 * conversation and forces a fresh {@code start} — so the task is a deterministic <b>state machine</b>:
 *
 * <ol>
 *   <li>introduce ourselves (Tymon Gajewski);</li>
 *   <li>ask the status of all three roads <em>and</em> give the "transport to a Zygfryd base" reason, in
 *       one message;</li>
 *   <li>react to the operator: give the password if asked to authenticate, give the food-transport cover
 *       if asked why; otherwise read the road statuses;</li>
 *   <li>request monitoring off on the one passable road;</li>
 *   <li>detect the {@code {FLG:...}} the Hub returns once it's unblocked.</li>
 * </ol>
 *
 * <p>Perception is split from control: {@link AudioService} (TTS/STT) and {@link RoadParser}/
 * {@link IntentClassifier} only <em>read</em> the operator; the sequence itself is fixed code. The task is
 * self-submitting (it drives the whole dialog and detects its own flag).
 */
@Component
class PhonecallTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(PhonecallTask.class);

    /** Body keys that may carry the operator's spoken reply as base64 audio, in order of preference. */
    private static final List<String> AUDIO_KEYS = List.of("audio", "mp3", "recording", "voice", "sound");
    /**
     * Body keys that may carry the operator's reply as plain text, in order of preference. {@code msg} is
     * first: recon confirmed the Hub puts the Polish operator/system content there, while {@code message}
     * is just an English status string ("Phonecall session started.").
     */
    private static final List<String> TEXT_KEYS =
            List.of("msg", "text", "transcription", "transcript", "message", "hint", "response", "content", "answer", "operator", "question");
    /** Substrings that signal the conversation was burned and must be restarted. */
    private static final List<String> BURN_WORDS = List.of("spalon", "od nowa", "rozpocznij", "zacznij", "zakończona", "przerwana");

    private static final String ROAD_SYSTEM = """
            Z wypowiedzi operatora wybierz JEDNĄ drogę, która jest PRZEJEZDNA (otwarta).
            Drogi to: RD224, RD472, RD820. Uwaga na negacje ("nie jest przejezdna", "nieprzejezdna" = zamknięta).
            Jeśli dokładnie jedna jest przejezdna, zwróć jej kod (np. "RD472"). Jeśli nie da się jednoznacznie
            ustalić, zwróć pusty łańcuch. Odpowiadasz wyłącznie polem struktury.
            """;

    private final PhonecallClient client;
    private final AudioService audio;
    private final ConversationScript script;
    private final IntentClassifier classifier;
    private final PhonecallProperties props;
    private final LlmService llm;
    private final ObjectMapper mapper;

    PhonecallTask(PhonecallClient client, AudioService audio, ConversationScript script,
                  IntentClassifier classifier, PhonecallProperties props, LlmService llm, ObjectMapper mapper) {
        this.client = client;
        this.audio = audio;
        this.script = script;
        this.classifier = classifier;
        this.props = props;
        this.llm = llm;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "phonecall";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "phonecall.solve")
    public Object solve() {
        // Pre-render the constant lines to MP3 before any timed session opens (only the disable request,
        // which names the resolved road, is rendered on demand).
        var greeting = ttsB64(script.greeting());
        var askRoads = ttsB64(script.askRoads());
        var password = ttsB64(script.password());
        var justify = ttsB64(script.justify());
        var clarify = ttsB64(script.clarify());
        var thanks = ttsB64(script.thanks());
        var lines = new Lines(greeting, askRoads, password, justify, clarify, thanks);

        int restarts = Math.max(0, props.maxRestarts());
        for (int attempt = 0; attempt <= restarts; attempt++) {
            log.info("phonecall conversation attempt {}/{}", attempt + 1, restarts + 1);
            var flag = runConversation(lines);
            if (flag.isPresent()) {
                log.info("FLAG → {}", flag.get());
                return Map.of("flag", flag.get(), "attempt", attempt + 1);
            }
            log.warn("Conversation ended without a flag (attempt {}). Restarting from scratch.", attempt + 1);
            // The operator's suspicion ramps up under rapid repeated sessions — space restarts out.
            if (attempt < restarts) {
                sleep(props.restartPauseMs());
            }
        }
        return Map.of("status", "no flag", "attempts", restarts + 1);
    }

    /** Drive one conversation from {@code start} to the flag (or to a burn / turn-cap). */
    private Optional<String> runConversation(Lines lines) {
        long t0 = System.currentTimeMillis();
        var resp = client.start();

        boolean greeted = false;
        boolean askedRoads = false;
        boolean askedDisable = false;
        String road = null;

        for (int turn = 1; turn <= props.maxTurns(); turn++) {
            var flag = resp.flag();
            if (flag.isPresent()) {
                log.info("Flag received after {} ms ({} turns)", System.currentTimeMillis() - t0, turn - 1);
                return flag;
            }

            String operator = listen(resp);
            hintOf(resp).ifPresent(h -> log.info("Hub hint: {}", h));
            if (isBurned(resp, operator)) {
                log.warn("Conversation appears burned: status={}, transcript='{}', hint={}",
                        resp.status(), operator, hintOf(resp).orElse("-"));
                return Optional.empty();
            }

            Intent intent = classifier.classify(operator);
            String sendB64;
            String label;

            if (intent == Intent.ASKS_PASSWORD) {
                sendB64 = lines.password();
                label = "password";
            } else if (intent == Intent.ASKS_WHY) {
                sendB64 = lines.justify();
                label = "justify";
            } else if (!greeted) {
                sendB64 = lines.greeting();
                greeted = true;
                label = "greeting";
            } else if (!askedRoads) {
                sendB64 = lines.askRoads();
                askedRoads = true;
                label = "ask-roads+reason";
            } else if (road == null) {
                road = resolveRoad(operator).orElse(null);
                if (road != null) {
                    sendB64 = ttsB64(script.disableMonitoring(road));
                    askedDisable = true;
                    label = "ask-disable " + road;
                } else {
                    sendB64 = lines.clarify();
                    label = "clarify-roads";
                }
            } else if (!askedDisable) {
                sendB64 = ttsB64(script.disableMonitoring(road));
                askedDisable = true;
                label = "ask-disable " + road;
            } else {
                sendB64 = lines.thanks();
                label = "thanks";
            }

            log.info("turn {}: operator='{}' intent={} -> say [{}]", turn, operator, intent, label);
            resp = client.audio(sendB64);
        }

        // Final check after the last reply (the flag may arrive on the turn that exhausts the cap).
        return resp.flag();
    }

    /** Extract the operator's turn from a response body: transcribe audio if present, else use text. */
    private String listen(PhonecallResponse resp) {
        String body = resp.body();
        if (body == null || body.isBlank()) {
            return "";
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception e) {
            return body.trim(); // not JSON — treat the whole body as text
        }

        String audioB64 = firstString(root, AUDIO_KEYS).filter(PhonecallTask::looksLikeAudio).orElse(null);
        if (audioB64 != null) {
            try {
                byte[] mp3 = Base64.getDecoder().decode(stripDataUri(audioB64));
                return audio.speechToText(mp3);
            } catch (Exception e) {
                log.warn("Failed to decode/transcribe operator audio, falling back to text: {}", e.getMessage());
            }
        }
        return firstString(root, TEXT_KEYS).orElseGet(() -> root.path("message").asText(""));
    }

    /** Single passable road: deterministic parser first, LLM fallback on ambiguity. */
    private Optional<String> resolveRoad(String transcript) {
        var deterministic = RoadParser.findPassable(transcript);
        if (deterministic.isPresent()) {
            log.info("RoadParser resolved passable road: {}", deterministic.get());
            return deterministic;
        }
        if (transcript == null || transcript.isBlank()) {
            return Optional.empty();
        }
        try {
            // Cap max_tokens: the output is just a road code, and OpenRouter 402s when the default
            // budget (65536) exceeds what the account can afford.
            var verdict = llm.extract(ROAD_SYSTEM, "WYPOWIEDŹ OPERATORA:\n" + transcript, props.classifyModel(), 256, RoadVerdict.class);
            if (verdict != null && verdict.road() != null) {
                String code = verdict.road().trim().toUpperCase(Locale.ROOT).replace(" ", "");
                if (RoadParser.ROADS.contains(code)) {
                    log.info("LLM resolved passable road: {}", code);
                    return Optional.of(code);
                }
            }
        } catch (Exception e) {
            log.warn("Road LLM fallback failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String ttsB64(String text) {
        byte[] mp3 = audio.textToSpeechMp3(text);
        if (mp3 == null || mp3.length == 0) {
            throw new IllegalStateException("TTS produced no audio for: " + text);
        }
        return Base64.getEncoder().encodeToString(mp3);
    }

    private boolean isBurned(PhonecallResponse resp, String operator) {
        if (resp.flag().isPresent()) {
            return false;
        }
        // A non-retryable client/server error (e.g. the recon-confirmed HTTP 400 on a rejected turn) means
        // this conversation can't continue — abort and restart from a fresh `start`.
        if (!resp.ok() && !resp.retryable()) {
            return true;
        }
        String haystack = ((resp.body() == null ? "" : resp.body()) + " " + (operator == null ? "" : operator))
                .toLowerCase(Locale.ROOT);
        return BURN_WORDS.stream().anyMatch(haystack::contains);
    }

    /** The Hub's English coaching note (the {@code hint} field), present on a rejected/suspicious turn. */
    private Optional<String> hintOf(PhonecallResponse resp) {
        String body = resp.body();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            return firstString(mapper.readTree(body), List.of("hint", "hints"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pausing between conversation attempts", e);
        }
    }

    private static Optional<String> firstString(JsonNode root, List<String> keys) {
        for (String key : keys) {
            var node = root.path(key);
            if (node.isTextual() && !node.asText().isBlank()) {
                return Optional.of(node.asText());
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeAudio(String value) {
        String v = stripDataUri(value);
        return v.length() > 100 && v.matches("[A-Za-z0-9+/=\\s]+");
    }

    private static String stripDataUri(String value) {
        int comma = value.indexOf(',');
        return value.startsWith("data:") && comma > 0 ? value.substring(comma + 1) : value;
    }

    /** Pre-rendered base64 MP3 of each constant line. */
    private record Lines(String greeting, String askRoads, String password, String justify, String clarify, String thanks) {
    }

    /** Structured-output shape for the road LLM fallback. */
    record RoadVerdict(String road) {
    }
}
