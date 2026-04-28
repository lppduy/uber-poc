package com.uberpoc.trip.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.With;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Builder
@With
@Table("trips")
public class Trip {

    @Id
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
}
