package com.uberpoc.trip.service.impl;

import com.uberpoc.trip.domain.Trip;
import com.uberpoc.trip.domain.TripStatus;
import com.uberpoc.trip.dto.response.TripResponse;
import com.uberpoc.trip.event.RideMatchedEvent;
import com.uberpoc.trip.event.TripStatusChangedEvent;
import com.uberpoc.trip.exception.InvalidStatusTransitionException;
import com.uberpoc.trip.exception.TripNotFoundException;
import com.uberpoc.trip.repository.TripRepository;
import com.uberpoc.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, TripStatusChangedEvent> kafkaTemplate;

    @Value("${app.redis.trip-state-ttl-seconds}")
    private long tripStateTtlSeconds;

    private static final String TOPIC_TRIP_STATUS = "trip.status.changed";
    private static final String REDIS_TRIP_KEY = "trip:status:";

    @Override
    public Mono<TripResponse> createTrip(RideMatchedEvent event) {
        Trip trip = Trip.builder()
                .id(event.getRideId())
                .riderId(event.getRiderId())
                .driverId(event.getDriverId())
                .status(TripStatus.MATCHED)
                .pickupLat(event.getPickupLat())
                .pickupLng(event.getPickupLng())
                .dropoffLat(event.getDropoffLat())
                .dropoffLng(event.getDropoffLng())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return r2dbcEntityTemplate.insert(trip)  // explicit INSERT, never UPDATE
                .flatMap(saved -> cacheStatus(saved.getId(), saved.getStatus())
                        .thenReturn(saved))
                .map(TripResponse::from)
                .doOnSuccess(t -> log.info("Trip created tripId={} status={}", t.getId(), t.getStatus()));
    }

    @Override
    public Mono<TripResponse> getTrip(String tripId) {
        // try cache first, fallback to DB
        return getCachedStatus(tripId)
                .flatMap(cachedStatus -> tripRepository.findById(tripId))
                .switchIfEmpty(tripRepository.findById(tripId))
                .switchIfEmpty(Mono.error(new TripNotFoundException(tripId)))
                .map(TripResponse::from);
    }

    @Override
    public Mono<TripResponse> updateStatus(String tripId, TripStatus newStatus) {
        return tripRepository.findById(tripId)
                .switchIfEmpty(Mono.error(new TripNotFoundException(tripId)))
                .flatMap(trip -> {
                    if (!trip.getStatus().canTransitionTo(newStatus)) {
                        return Mono.error(new InvalidStatusTransitionException(trip.getStatus(), newStatus));
                    }

                    TripStatus previousStatus = trip.getStatus();
                    Trip updated = trip.withStatus(newStatus).withUpdatedAt(Instant.now());

                    return tripRepository.save(updated)
                            .flatMap(saved -> cacheStatus(saved.getId(), saved.getStatus())
                                    .then(publishStatusChanged(saved, previousStatus))
                                    .thenReturn(saved));
                })
                .map(TripResponse::from)
                .doOnSuccess(t -> log.info("Trip status updated tripId={} status={}", t.getId(), t.getStatus()));
    }

    // ── private helpers ────────────────────────────────────────────────────

    private Mono<Boolean> cacheStatus(String tripId, TripStatus status) {
        return redisTemplate.opsForValue()
                .set(REDIS_TRIP_KEY + tripId,
                        status.name(),
                        Duration.ofSeconds(tripStateTtlSeconds));
    }

    private Mono<String> getCachedStatus(String tripId) {
        return redisTemplate.opsForValue().get(REDIS_TRIP_KEY + tripId);
    }

    private Mono<Void> publishStatusChanged(Trip trip, TripStatus previousStatus) {
        TripStatusChangedEvent event = TripStatusChangedEvent.builder()
                .tripId(trip.getId())
                .riderId(trip.getRiderId())
                .driverId(trip.getDriverId())
                .previousStatus(previousStatus)
                .newStatus(trip.getStatus())
                .changedAt(Instant.now())
                .build();

        return Mono.fromFuture(
                kafkaTemplate.send(TOPIC_TRIP_STATUS, trip.getId(), event).toCompletableFuture()
        ).then();
    }
}
