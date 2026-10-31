package com.riseup.config;

import com.riseup.clock.Clock;
import com.riseup.clock.SystemClock;
import com.riseup.scheduling.JobScheduler;
import com.riseup.scheduling.ManualJobScheduler;
import com.riseup.scheduling.SpringJobScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return new SystemClock();
    }

    @Bean
    @ConditionalOnProperty(name = "riseup.enable-scheduler", havingValue = "true", matchIfMissing = true)
    public JobScheduler springJobScheduler() {
        return new SpringJobScheduler();
    }

    @Bean
    @ConditionalOnProperty(name = "riseup.enable-scheduler", havingValue = "false")
    public JobScheduler manualJobScheduler() {
        return new ManualJobScheduler();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(RiseUpProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = properties.getCorsOrigins().split(",");
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(origins)
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true);
                registry.addMapping("/ws/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("*");
            }
        };
    }
}
