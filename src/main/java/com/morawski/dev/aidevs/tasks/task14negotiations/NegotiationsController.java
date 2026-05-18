package com.morawski.dev.aidevs.tasks.task14negotiations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public tool endpoint the Centrala agent calls. It POSTs {@code {"params":"<item request>"}} and expects
 * {@code {"output":"<cities>"}} back. We always return a non-empty 200 (even on error or no match): a tool
 * that gives no answer makes the agent abort its whole run. Every request is logged — that log (mirrored by
 * the Hub's {@code /debug} panel) is the recon for how the agent actually phrases its requests.
 */
@RestController
class NegotiationsController {

    private static final Logger log = LoggerFactory.getLogger(NegotiationsController.class);

    private final NegotiationsService service;

    NegotiationsController(NegotiationsService service) {
        this.service = service;
    }

    /** Health-check (some callers GET first); also keeps the reply ≥ 4 bytes. */
    @GetMapping("/api/negotiations")
    ToolResponse health() {
        return new ToolResponse("ok - podaj przedmiot w polu params");
    }

    @PostMapping("/api/negotiations")
    ToolResponse handle(@RequestBody ToolRequest req) {
        log.info("Tool request params: {}", req.params());
        try {
            var output = service.lookup(req.params());
            log.info("Tool response: {}", output);
            return new ToolResponse(output);
        } catch (Exception e) {
            // Never leave the agent without a response.
            log.error("Error handling tool request: {}", req.params(), e);
            return new ToolResponse("Blad chwilowy, sprobuj ponownie.");
        }
    }
}
