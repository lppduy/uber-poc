package com.uberpoc.matching.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter rideRequestCounter(MeterRegistry registry) {
        return Counter.builder("matching.ride.requests.total")
                .description("Total ride requests received")
                .register(registry);
    }

    @Bean
    public Counter rideMatchedCounter(MeterRegistry registry) {
        return Counter.builder("matching.ride.matched.total")
                .description("Total rides successfully matched")
                .register(registry);
    }

    @Bean
    public Counter rideNoDriverCounter(MeterRegistry registry) {
        return Counter.builder("matching.ride.no_driver.total")
                .description("Total ride requests with no driver found")
                .register(registry);
    }
}
