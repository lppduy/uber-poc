package com.uberpoc.location.service;

import com.uberpoc.location.dto.request.UpdateLocationRequest;
import com.uberpoc.location.dto.response.NearbyDriverResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LocationService {

    Mono<Void> updateDriverLocation(String driverId, UpdateLocationRequest request);

    Flux<NearbyDriverResponse> findNearbyDrivers(double lat, double lng, double radiusKm);
}
