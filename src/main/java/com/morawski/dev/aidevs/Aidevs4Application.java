package com.morawski.dev.aidevs;

import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Aidevs4Application {

    public static void main(String[] args) {
        var app = new SpringApplication(Aidevs4Application.class);
        app.addInitializers(new DotenvApplicationInitializer());
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}
