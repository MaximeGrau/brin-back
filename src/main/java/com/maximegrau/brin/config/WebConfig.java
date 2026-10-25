package com.maximegrau.brin.config;

import com.maximegrau.brin.BrinApplication;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Chemin de base du contrat ({@code servers.url}), absent des interfaces générées. */
    public static final String API_BASE_PATH = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_BASE_PATH, type ->
                type.isAnnotationPresent(RestController.class)
                        && type.getPackageName().startsWith(BrinApplication.class.getPackageName()));
    }

    /** {@code /auth/oauth/google/…} : valeurs en minuscules du contrat, que la conversion par défaut ne lit pas. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, com.maximegrau.brin.api.model.AuthProvider.class,
                com.maximegrau.brin.api.model.AuthProvider::fromValue);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
