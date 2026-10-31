package com.riseup;

import com.riseup.config.RiseUpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RiseUpProperties.class)
public class RiseUpApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiseUpApplication.class, args);
    }
}
