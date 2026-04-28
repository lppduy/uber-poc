package com.uberpoc.trip.kafka;

import com.uberpoc.trip.event.RideMatchedEvent;
import com.uberpoc.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideMatchedConsumer {

    private final TripService tripService;

    @KafkaListener(topics = "ride.matched", groupId = "trip-service")
    public void onRideMatched(
            @Payload RideMatchedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed ride.matched rideId={} partition={} offset={}",
                event.getRideId(), partition, offset);

        tripService.createTrip(event)
                .doOnError(ex -> log.error("Failed to create trip for rideId={}", event.getRideId(), ex))
                .subscribe();
    }
}
