package com.morawski.dev.aidevs.tasks.task24goingthere;

/**
 * Structured output of the radio-hint classifier: which side the rock is on, in the rocket's command
 * frame. Spring AI turns this record into the response schema and parses the model's reply back into it.
 */
record HintVerdict(AvoidSide side) {
}
