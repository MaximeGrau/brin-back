package com.maximegrau.brin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les méthodes {@code @Scheduled} (Spring Boot fournit alors le TaskScheduler). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
