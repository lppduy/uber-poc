package com.uberpoc.matching.service.impl;

import com.uberpoc.matching.domain.DriverResponse;
import com.uberpoc.matching.domain.RideStatus;
import com.uberpoc.matching.dto.request.RideRequestDto;
import com.uberpoc.matching.dto.response.MatchResultResponse;
import com.uberpoc.matching.event.RideMatchedEvent;
import com.uberpoc.matching.exception.NoDriverAvailableException;
import com.uberpoc.matching.exception.RideNotFoundException;
import com.uberpoc.matching.service.MatchingService;
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
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingServiceImpl implements MatchingService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, RideMatchedEvent> kafkaTemplate;

    @Value("${app.geo.drivers-key}")
    private String driversGeoKey;

    @Value("${app.geo.max-candidates}")
    private int maxCandidates;

    @Value("${app.geo.search-radius-km}")
    private double searchRadiusKm;

    @Value("${app.dispatch.timeout-seconds}")
    private int timeoutSeconds;

    private static final String TOPIC_RIDE_MATCHED = "ride.matched";

    /**
     * pending dispatches: rideId -> Sink that resolves when driver responds.
     * DriverResponse is the signal value; complete = accepted, error = declined/timeout.
     */
    private final Map<String, Sinks.One<DriverResponse>> pendingDispatches = new ConcurrentHashMap<>();

    @Override
    public Mono<MatchResultResponse> requestRide(RideRequestDto request) {
        String rideId = UUID.randomUUID().toString();
        log.info("Ride request rideId={} riderId={}", rideId, request.getRiderId());

        return findNearestDrivers(request.getPickupLat(), request.getPickupLng())
                .flatMap(candidates -> {
                    if (candidates.isEmpty()) {
                        return Mono.error(new NoDriverAvailableException());
                    }
                    return dispatchSequentially(rideId, request, candidates, 0);
                });
    }

    @Override
    public Mono<Void> driverRespond(String rideId, String driverId, DriverResponse response) {
        Sinks.One<DriverResponse> sink = pendingDispatches.get(rideId);
        if (sink == null) {
            return Mono.error(new RideNotFoundException(rideId));
        }

        log.info("Driver response rideId={} driverId={} response={}", rideId, driverId, response);

        if (response == DriverResponse.ACCEPTED) {
            sink.tryEmitValue(response);
        } else {
            sink.tryEmitError(new RuntimeException("Driver " + driverId + " declined"));
        }
        return Mono.empty();
    }

    // ── private helpers ────────────────────────────────────────────────────

    private Mono<List<NearbyDriver>> findNearestDrivers(double lat, double lng) {
        Circle circle = new Circle(
                new Point(lng, lat),
                new Distance(searchRadiusKm, Metrics.KILOMETERS)
        );

        return redisTemplate.opsForGeo()
                .radius(driversGeoKey, circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .includeCoordinates()
                                .sortAscending()
                                .limit(maxCandidates))
                .map(result -> new NearbyDriver(
                        result.getContent().getName(),
                        result.getContent().getPoint().getY(),
                        result.getContent().getPoint().getX(),
                        result.getDistance().getValue()))
                .collectList();
    }

    private Mono<MatchResultResponse> dispatchSequentially(
            String rideId, RideRequestDto request, List<NearbyDriver> candidates, int index) {

        if (index >= candidates.size()) {
            log.warn("All {} candidates declined for rideId={}", candidates.size(), rideId);
            return Mono.error(new NoDriverAvailableException());
        }

        NearbyDriver candidate = candidates.get(index);
        log.info("Dispatching rideId={} to driverId={} (attempt {}/{})",
                rideId, candidate.driverId(), index + 1, candidates.size());

        Sinks.One<DriverResponse> sink = Sinks.one();
        pendingDispatches.put(rideId, sink);

        return sink.asMono()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .flatMap(response -> {
                    // driver accepted
                    pendingDispatches.remove(rideId);
                    return onDriverAccepted(rideId, request, candidate);
                })
                .onErrorResume(ex -> {
                    // declined or timed out — try next driver
                    pendingDispatches.remove(rideId);
                    log.info("rideId={} driver={} no-accept ({}), trying next",
                            rideId, candidate.driverId(), ex.getMessage());
                    return dispatchSequentially(rideId, request, candidates, index + 1);
                });
    }

    private Mono<MatchResultResponse> onDriverAccepted(
            String rideId, RideRequestDto request, NearbyDriver driver) {

        RideMatchedEvent event = RideMatchedEvent.builder()
                .rideId(rideId)
                .riderId(request.getRiderId())
                .driverId(driver.driverId())
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .dropoffLat(request.getDropoffLat())
                .dropoffLng(request.getDropoffLng())
                .matchedAt(Instant.now())
                .build();

        return Mono.fromFuture(
                kafkaTemplate.send(TOPIC_RIDE_MATCHED, rideId, event).toCompletableFuture()
        ).thenReturn(MatchResultResponse.builder()
                .rideId(rideId)
                .status(RideStatus.MATCHED)
                .driverId(driver.driverId())
                .driverLat(driver.lat())
                .driverLng(driver.lng())
                .distanceKm(driver.distanceKm())
                .build());
    }

    private record NearbyDriver(String driverId, double lat, double lng, double distanceKm) {}
}
