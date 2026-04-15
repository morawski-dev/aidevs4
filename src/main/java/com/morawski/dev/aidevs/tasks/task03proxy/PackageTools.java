package com.morawski.dev.aidevs.tasks.task03proxy;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Tools exposed to the LLM for the packages API (Spring AI generates the JSON schema). */
@Component
class PackageTools {

    private final PackagesApiClient api;

    PackageTools(PackagesApiClient api) {
        this.api = api;
    }

    @Tool(name = "check_package",
            description = "Sprawdza aktualny status i lokalizację paczki na podstawie jej identyfikatora.")
    String checkPackage(
            @ToolParam(description = "Identyfikator paczki, np. PKG12345678") String packageid) {
        return api.check(packageid);
    }

    @Tool(name = "redirect_package",
            description = "Przekierowuje paczkę do wskazanego magazynu docelowego, "
                    + "używając kodu zabezpieczającego podanego przez operatora.")
    String redirectPackage(
            @ToolParam(description = "Identyfikator paczki, np. PKG12345678") String packageid,
            @ToolParam(description = "Kod magazynu docelowego, np. PWR1234PL") String destination,
            @ToolParam(description = "Kod zabezpieczający podany przez operatora") String code) {
        return api.redirect(packageid, destination, code);
    }
}
