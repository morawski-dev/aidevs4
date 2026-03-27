package com.morawski.dev.aidevs.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Copies the chat prompt and completion content onto the tracing span as
 * {@code gen_ai.prompt} / {@code gen_ai.completion} attributes.
 * <p>
 * Spring AI 1.x deliberately only <em>logs</em> this content (via {@code log-prompt}
 * / {@code log-completion}) and no longer puts it on the OTel span. Langfuse reads
 * input/output from those span attributes, so without this filter generation spans
 * show up with empty input/output.
 */
@Component
class ChatModelContentObservationFilter implements ObservationFilter {

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatContext)) {
            return context;
        }

        var prompts = processPrompts(chatContext);
        var completions = processCompletions(chatContext);

        chatContext.addHighCardinalityKeyValue(KeyValue.of(
                "gen_ai.prompt", ObservabilityHelper.concatenateStrings(prompts)));
        chatContext.addHighCardinalityKeyValue(KeyValue.of(
                "gen_ai.completion", ObservabilityHelper.concatenateStrings(completions)));

        return chatContext;
    }

    private List<String> processPrompts(ChatModelObservationContext ctx) {
        if (CollectionUtils.isEmpty(ctx.getRequest().getInstructions())) {
            return List.of();
        }
        return ctx.getRequest().getInstructions().stream()
                .map(Content::getText)
                .toList();
    }

    private List<String> processCompletions(ChatModelObservationContext ctx) {
        var response = ctx.getResponse();
        if (response == null || response.getResults() == null) {
            return List.of();
        }
        return response.getResults().stream()
                .filter(g -> g.getOutput() != null && StringUtils.hasText(g.getOutput().getText()))
                .map(g -> g.getOutput().getText())
                .toList();
    }
}