package com.morawski.dev.aidevs.tasks.task14negotiations;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured output of the LLM item-disambiguation step: which catalog item (by its short {@code code})
 * best matches the agent's natural-language request, from a shortlist of candidates. {@code found=false}
 * (and a blank {@code code}) means none of the candidates is the requested item.
 */
@JsonClassDescription("The single catalog item that best matches the user's request, chosen from the given candidates.")
record ItemMatch(
        @JsonPropertyDescription("true if one candidate clearly matches the requested item, false otherwise")
        boolean found,
        @JsonPropertyDescription("the exact 'code' of the matching candidate, or empty string when found is false")
        String code
) {
}
