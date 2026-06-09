package com.morawski.dev.aidevs.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.AudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * {@link AudioService} backed by the OpenAI audio API (a separate provider/key from the OpenRouter chat
 * stack, which doesn't serve TTS/Whisper):
 * <ul>
 *   <li>TTS — {@code POST /v1/audio/speech} with {@code {model, voice, input, response_format:"mp3"}}
 *       returns raw MP3 bytes;</li>
 *   <li>STT — {@code POST /v1/audio/transcriptions} (multipart: {@code file}, {@code model},
 *       {@code language}) returns the transcript.</li>
 * </ul>
 *
 * <p>Its own {@link RestClient} (base URL + bearer key from {@link AudioProperties}) keeps it isolated
 * from Spring AI's auto-configured OpenAI client. Calls fail fast with a clear message when no key is
 * configured (the Spring context still loads — the bean is constructed lazily of any network use).
 */
@Service
public class OpenAiAudioService implements AudioService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAudioService.class);

    private final AudioProperties props;
    private final RestClient http;
    private final ObjectMapper mapper;

    OpenAiAudioService(AudioProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = RestClient.builder()
                .baseUrl(StringUtils.hasText(props.baseUrl()) ? props.baseUrl() : "https://api.openai.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (props.apiKey() == null ? "" : props.apiKey()))
                .build();
    }

    @Override
    public byte[] textToSpeechMp3(String text) {
        requireKey();
        var body = Map.of(
                "model", props.ttsModel(),
                "voice", props.ttsVoice(),
                "input", text,
                "response_format", "mp3");
        byte[] mp3 = http.post()
                .uri("/v1/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(byte[].class);
        int len = mp3 == null ? 0 : mp3.length;
        log.info("TTS '{}' -> {} bytes MP3", abbreviate(text), len);
        return mp3;
    }

    @Override
    public String speechToText(byte[] mp3) {
        requireKey();
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(mp3) {
            @Override
            public String getFilename() {
                return "audio.mp3"; // the multipart filename a transcription endpoint requires
            }
        });
        parts.add("model", props.sttModel());
        if (StringUtils.hasText(props.language())) {
            parts.add("language", props.language());
        }
        parts.add("response_format", "json");

        String raw = http.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(String.class);

        String text = parseTranscript(raw);
        log.info("STT {} bytes MP3 -> '{}'", mp3 == null ? 0 : mp3.length, abbreviate(text));
        return text;
    }

    /** The endpoint returns {@code {"text":"..."}} for {@code response_format=json}; fall back to the raw body. */
    private String parseTranscript(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            var node = mapper.readTree(raw);
            var text = node.path("text");
            if (text.isTextual()) {
                return text.asText();
            }
        } catch (Exception e) {
            log.warn("Transcription response was not JSON, using raw body", e);
        }
        return raw.trim();
    }

    private void requireKey() {
        if (!StringUtils.hasText(props.apiKey())) {
            throw new IllegalStateException(
                    "Audio provider API key is missing — set OPENAI_API_KEY (aidevs.audio.api-key). "
                            + "OpenRouter does not serve TTS/Whisper, so the phonecall task needs a direct audio key.");
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 80 ? s : s.substring(0, 80) + "…";
    }
}
