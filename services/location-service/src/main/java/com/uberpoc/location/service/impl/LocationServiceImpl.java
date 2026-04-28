package com.uberpoc.location.service.impl;

import com.uberpoc.location.dto.request.UpdateLocationRequest;
import com.uberpoc.location.dto.response.NearbyDriverResponse;
import com.uberpoc.location.event.DriverLocationEvent;
import com.uberpoc.location.service.LocationService;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, DriverLocationEvent> kafkaTemplate;
    private final Counter locationUpdateCounter;
    private final Counter nearbyQueryCounter;

    @Value("${app.geo.drivers-key}")
    private String driversGeoKey;

    @Value("${app.geo.max-results}")
    private long maxResults;

    private static final String TOPIC_LOCATION = "driver.location.updated";

    @Override
    public Mono<Void> updateDriverLocation(String driverId, UpdateLocationRequest request) {
        Point point = new Point(request.getLongitude(), request.getLatitude());

        return redisTemplate.opsForGeo()
                .add(driversGeoKey, point, driverId)
                .doOnSuccess(count -> {
                    log.debug("Updated location for driver={}", driverId);
                    locationUpdateCounter.increment();
                })
                .then(publishLocationEvent(driverId, request));
    }

    @Override
    public Flux<NearbyDriverResponse> findNearbyDrivers(double lat, double lng, double radiusKm) {
        nearbyQueryCounter.increment();
        Circle circle = new Circle(
                new Point(lng, lat),
                new Distance(radiusKm, Metrics.KILOMETERS)
        );

        return redisTemplate.opsForGeo()
                .radius(driversGeoKey, circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .includeCoordinates()
                                .sortAscending()
                                .limit(maxResults))
                .map(result -> NearbyDriverResponse.builder()
                        .driverId(result.getContent().getName())
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceKm(result.getDistance().getValue())
                        .build());
    }

    private Mono<Void> publishLocationEvent(String driverId, UpdateLocationRequest request) {
        DriverLocationEvent event = DriverLocationEvent.builder()
                .driverId(driverId)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .timestamp(Instant.now())
                .build();

        return Mono.fromFuture(
                kafkaTemplate.send(TOPIC_LOCATION, driverId, event).toCompletableFuture()
        ).then();
    }
}
