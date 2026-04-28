package com.uberpoc.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter tripStatusBroadcastCounter(MeterRegistry registry) {
        return Counter.builder("websocket.trip_status.broadcasts.total")
                .description("Total trip status events broadcasted over STOMP")
                .register(registry);
    }
}
