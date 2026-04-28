package com.uberpoc.matching.service;

import com.uberpoc.matching.domain.DriverResponse;
import com.uberpoc.matching.dto.request.RideRequestDto;
import com.uberpoc.matching.dto.response.MatchResultResponse;
import reactor.core.publisher.Mono;

public interface MatchingService {

    Mono<MatchResultResponse> requestRide(RideRequestDto request);

    Mono<Void> driverRespond(String rideId, String driverId, DriverResponse response);
}
