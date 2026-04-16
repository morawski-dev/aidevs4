package com.morawski.dev.aidevs.tasks.task03proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public HTTP endpoint the logistics operator (Hub) talks to. */
@RestController
class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private final ConversationService conversation;

    ProxyController(ConversationService conversation) {
        this.conversation = conversation;
    }

    /** Health-check: the Hub sends one GET before starting the conversation. */
    @GetMapping("/proxy")
    ProxyResponse health() {
        return new ProxyResponse("ok");
    }

    @PostMapping("/proxy")
    ProxyResponse handle(@RequestBody ProxyRequest req) {
        log.info("Incoming [{}]: {}", req.sessionID(), req.msg());
        try {
            var reply = conversation.reply(req.sessionID(), req.msg());
            log.info("Reply [{}]: {}", req.sessionID(), reply);
            return new ProxyResponse(reply);
        } catch (Exception e) {
            // Never leak an error to the operator — stay in character.
            log.error("Error handling [{}]", req.sessionID(), e);
            return new ProxyResponse("Sekundę, zaraz to ogarnę — możesz powtórzyć?");
        }
    }
}
