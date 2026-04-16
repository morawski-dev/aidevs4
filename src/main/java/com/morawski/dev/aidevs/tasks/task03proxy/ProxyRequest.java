package com.morawski.dev.aidevs.tasks.task03proxy;

/** Incoming message from the logistics operator (Hub). */
record ProxyRequest(String sessionID, String msg) {
}
