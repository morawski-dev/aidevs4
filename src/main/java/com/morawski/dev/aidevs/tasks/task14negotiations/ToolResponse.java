package com.morawski.dev.aidevs.tasks.task14negotiations;

/** Reply the agent expects from our tool endpoint: {@code {"output":"<answer>"}} (4..500 bytes). */
record ToolResponse(String output) {
}
