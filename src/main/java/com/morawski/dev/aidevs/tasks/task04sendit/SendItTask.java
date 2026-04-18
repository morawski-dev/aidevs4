package com.morawski.dev.aidevs.tasks.task04sendit;

import com.morawski.dev.aidevs.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * S01E04 ({@code sendit}) — submit a correctly filled SPK transport declaration.
 *
 * <p>The declaration is verified by the Hub for both values and exact formatting. The two hidden
 * unknowns (kategoria giving 0 PP, and the Gdańsk→Żarnowiec route code) were resolved from the SPK
 * documentation; see {@link Shipment} for the full reasoning and sources. {@link DeclarationBuilder}
 * reproduces the {@code zalacznik-E.md} template 1:1.
 *
 * <p>The whole declaration is logged before submission so it can be eyeballed against the template.
 * If the Hub rejects it, read the {@code message} on the resulting {@code HubException} — it points
 * at the offending field/format — then adjust {@link Shipment#forTask()} and rerun.
 */
@Component
class SendItTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SendItTask.class);

    private final DeclarationBuilder builder;

    SendItTask(DeclarationBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String name() {
        return "sendit";
    }

    @Override
    public Object solve() {
        var shipment = Shipment.forTask();
        var declaration = builder.build(shipment);
        log.info("SPK declaration to submit:\n{}", declaration);
        return Map.of("declaration", declaration);
    }
}
