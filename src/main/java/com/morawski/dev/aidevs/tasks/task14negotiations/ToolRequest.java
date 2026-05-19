package com.morawski.dev.aidevs.tasks.task14negotiations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Body the agent POSTs to our tool endpoint: {@code {"params":"<natural-language item request>"}}.
 * Unknown fields are ignored so an unexpected extra key never turns into a 400 (which would leave the
 * agent with no response and abort its run).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ToolRequest(String params) {
}
