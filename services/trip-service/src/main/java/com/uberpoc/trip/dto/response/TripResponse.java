package com.uberpoc.trip.dto.response;

import com.uberpoc.trip.domain.Trip;
import com.uberpoc.trip.domain.TripStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TripResponse {
    private String id;
    private String riderId;
    private String driverId;
    private TripStatus status;
    private double pickupLat;
    private double pickupLng;
    private double dropoffLat;
    private double dropoffLng;
    private Instant createdAt;
    private Instant updatedAt;

    public static TripResponse from(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .riderId(trip.getRiderId())
                .driverId(trip.getDriverId())
                .status(trip.getStatus())
                .pickupLat(trip.getPickupLat())
                .pickupLng(trip.getPickupLng())
                .dropoffLat(trip.getDropoffLat())
                .dropoffLng(trip.getDropoffLng())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }
}
