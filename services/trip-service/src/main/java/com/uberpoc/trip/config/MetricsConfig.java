package com.uberpoc.trip.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter tripCreatedCounter(MeterRegistry registry) {
        return Counter.builder("trip.created.total")
                .description("Total trips created")
                .register(registry);
    }

    @Bean
    public Counter tripCompletedCounter(MeterRegistry registry) {
        return Counter.builder("trip.completed.total")
                .description("Total trips completed successfully")
                .register(registry);
    }

    @Bean
    public Counter tripCancelledCounter(MeterRegistry registry) {
        return Counter.builder("trip.cancelled.total")
                .description("Total trips cancelled")
                .register(registry);
    }
}
