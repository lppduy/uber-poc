package com.uberpoc.location.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter locationUpdateCounter(MeterRegistry registry) {
        return Counter.builder("location.updates.total")
                .description("Total driver location updates received")
                .register(registry);
    }

    @Bean
    public Counter nearbyQueryCounter(MeterRegistry registry) {
        return Counter.builder("location.nearby.queries.total")
                .description("Total nearby driver queries")
                .register(registry);
    }
}
