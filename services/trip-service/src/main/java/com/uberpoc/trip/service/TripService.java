package com.uberpoc.trip.service;

import com.uberpoc.trip.domain.TripStatus;
import com.uberpoc.trip.dto.response.TripResponse;
import com.uberpoc.trip.event.RideMatchedEvent;
import reactor.core.publisher.Mono;

public interface TripService {

    Mono<TripResponse> createTrip(RideMatchedEvent event);

    Mono<TripResponse> getTrip(String tripId);

    Mono<TripResponse> updateStatus(String tripId, TripStatus newStatus);
}
