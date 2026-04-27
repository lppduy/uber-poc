package com.uberpoc.location.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class DriverLocation {
    private String driverId;
    private double latitude;
    private double longitude;
    private DriverStatus status;
    private Instant updatedAt;
}
