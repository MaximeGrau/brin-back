package com.maximegrau.brin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BrinApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrinApplication.class, args);
    }
}
