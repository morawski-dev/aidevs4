package com.morawski.dev.aidevs;

import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.Arrays;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Aidevs4Application {

    public static void main(String[] args) {
        var app = new SpringApplication(Aidevs4Application.class);
        app.addInitializers(new DotenvApplicationInitializer());
        // Compute tasks run once and exit (NONE). The proxy task needs a long-lived
        // HTTP server, so bring up the embedded servlet container only for it.
        app.setWebApplicationType(needsWebServer(args) ? WebApplicationType.SERVLET : WebApplicationType.NONE);
        app.run(args);
    }

    private static boolean needsWebServer(String[] args) {
        return Arrays.stream(args).anyMatch("--task=proxy"::equals);
    }
}
