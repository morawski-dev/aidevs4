package com.morawski.dev.aidevs.tasks.task22phonecall;

/**
 * What the operator's transcribed turn is asking of us — the branch the state machine reacts to.
 *
 * <ul>
 *   <li>{@link #ASKS_PASSWORD} — wants us to authenticate (we reply with the secret password);</li>
 *   <li>{@link #ASKS_WHY} — wants to know why we want monitoring off (we give the food-transport cover);</li>
 *   <li>{@link #CONFIRMS_DISABLED} — confirms the monitoring is off (we wrap up / wait for the flag);</li>
 *   <li>{@link #OTHER} — anything else (greeting, road statuses, small talk) — the scripted sequence advances.</li>
 * </ul>
 */
enum Intent {
    ASKS_PASSWORD,
    ASKS_WHY,
    CONFIRMS_DISABLED,
    OTHER
}
