package com.uberpoc.gateway.kafka;

import com.uberpoc.gateway.event.TripStatusChangedEvent;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripStatusConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final Counter tripStatusBroadcastCounter;

    private static final String STOMP_TOPIC_PREFIX = "/topic/trips/";

    @KafkaListener(topics = "trip.status.changed", groupId = "websocket-gateway")
    public void onTripStatusChanged(
            @Payload TripStatusChangedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed trip.status.changed tripId={} {} -> {} partition={} offset={}",
                event.getTripId(), event.getPreviousStatus(), event.getNewStatus(), partition, offset);

        String destination = STOMP_TOPIC_PREFIX + event.getTripId();
        messagingTemplate.convertAndSend(destination, event);

        tripStatusBroadcastCounter.increment();
        log.debug("Broadcasted to STOMP destination={}", destination);
    }
}
