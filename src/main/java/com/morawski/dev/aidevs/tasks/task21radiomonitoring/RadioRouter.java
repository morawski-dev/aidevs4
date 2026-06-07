package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import com.morawski.dev.aidevs.config.RadiomonitoringProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * The cost-aware router — the heart of task21. Given one {@link ListenSignal}, it decides locally what
 * to do with the material and returns at most one {@link Snippet} of model-ready text:
 *
 * <ul>
 *   <li>a text {@code transcription} is kept as-is (free; noise lines are harmless, the synthesizer ignores them);</li>
 *   <li>an {@code attachment} is base64-decoded locally and {@link MediaKind#classify classified} by magic bytes:
 *       {@code IMAGE} → OCR via a vision model; {@code JSON_OR_TEXT} → decoded to a string locally (free);
 *       {@code AUDIO} → transcribed via an audio model; {@code NOISE} → dropped.</li>
 * </ul>
 *
 * <p>Crucially, raw base64 bytes are never handed to an LLM — only genuine images/audio reach a model,
 * keeping token cost down (the explicit goal of the task).
 */
@Component
class RadioRouter {

    private static final Logger log = LoggerFactory.getLogger(RadioRouter.class);

    private static final String OCR_PROMPT = """
            This image was intercepted from a radio transmission and may contain text relevant to
            locating a city. Transcribe ALL readable text exactly (any language, keep numbers, names,
            phone numbers and figures verbatim). If there is no readable text, briefly describe what
            the image shows. Return only the transcription/description.""";

    private static final String AUDIO_PROMPT = """
            This audio was intercepted from a radio transmission. Transcribe everything spoken, verbatim
            (keep numbers, names, phone numbers and figures). Return only the transcript.""";

    private final LlmService llm;
    private final RadiomonitoringProperties props;

    RadioRouter(LlmService llm, RadiomonitoringProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /** Route one chunk to a single useful {@link Snippet}, or {@link Optional#empty()} for noise/skip. */
    Optional<Snippet> route(ListenSignal s, int index) {
        if (s.hasAttachment()) {
            return routeAttachment(s, index);
        }
        if (s.hasText()) {
            return Optional.of(new Snippet("listen#" + index + " text", s.transcription()));
        }
        return Optional.empty();
    }

    private Optional<Snippet> routeAttachment(ListenSignal s, int index) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(s.attachment().trim());
        } catch (IllegalArgumentException e) {
            log.warn("listen#{}: attachment is not valid base64 ({}); skipping", index, e.getMessage());
            return Optional.empty();
        }

        MediaKind kind = MediaKind.classify(s.meta(), bytes);
        log.info("listen#{}: attachment meta='{}' filesize={} decoded={}B -> {}",
                index, s.meta(), s.filesize(), bytes.length, kind);

        return switch (kind) {
            case IMAGE -> ocrImage(bytes, s.meta(), index);
            case AUDIO -> transcribeAudio(bytes, s.meta(), index);
            case JSON_OR_TEXT -> Optional.of(new Snippet(
                    "listen#" + index + " file", new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
            case NOISE, TEXT -> {
                log.info("listen#{}: dropping {} attachment ({}B) as not worth model analysis", index, kind, bytes.length);
                yield Optional.empty();
            }
        };
    }

    private Optional<Snippet> ocrImage(byte[] bytes, String meta, int index) {
        try {
            var read = llm.extractFromImage(OCR_PROMPT, bytes, imageMime(meta), props.visionModel(), ImageText.class);
            return snippetFrom(read, "listen#" + index + " image");
        } catch (Exception e) {
            log.warn("listen#{}: vision OCR failed ({}); skipping image", index, e.toString());
            return Optional.empty();
        }
    }

    private Optional<Snippet> transcribeAudio(byte[] bytes, String meta, int index) {
        try {
            // Plain-text transcription (not structured output): audio-preview models may not support
            // response_format json schema alongside an audio input.
            String text = llm.transcribe(AUDIO_PROMPT, bytes, audioMime(meta, bytes), props.audioModel());
            if (!StringUtils.hasText(text)) {
                return Optional.empty();
            }
            return Optional.of(new Snippet("listen#" + index + " audio", text.trim()));
        } catch (Exception e) {
            log.warn("listen#{}: audio transcription failed ({}); skipping audio", index, e.toString());
            return Optional.empty();
        }
    }

    private Optional<Snippet> snippetFrom(ImageText read, String source) {
        if (read == null || !StringUtils.hasText(read.text())) {
            return Optional.empty();
        }
        return Optional.of(new Snippet(source, read.text().trim()));
    }

    /** A valid image {@link MimeType} for the {@code media()} attachment — prefer the declared meta, else PNG. */
    private static MimeType imageMime(String meta) {
        MimeType parsed = tryParse(meta);
        return parsed != null && "image".equals(parsed.getType()) ? parsed : MimeTypeUtils.IMAGE_PNG;
    }

    /**
     * Mime the provider maps to an {@code input_audio} part. OpenAI/OpenRouter only accept formats
     * {@code mp3} and {@code wav}, so we normalise (e.g. {@code audio/mpeg} → {@code audio/mp3}) and
     * pick from the magic bytes when the declared meta is unhelpful.
     */
    private static MimeType audioMime(String meta, byte[] bytes) {
        String mime = meta == null ? "" : meta.trim().toLowerCase(Locale.ROOT);
        if (mime.contains("wav") || isRiffWave(bytes)) {
            return MimeType.valueOf("audio/wav");
        }
        return MimeType.valueOf("audio/mp3");
    }

    private static boolean isRiffWave(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
    }

    private static MimeType tryParse(String meta) {
        if (!StringUtils.hasText(meta)) {
            return null;
        }
        try {
            return MimeType.valueOf(meta.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Structured-output holder for text read out of an image/audio attachment. */
    record ImageText(String text) {
    }
}
