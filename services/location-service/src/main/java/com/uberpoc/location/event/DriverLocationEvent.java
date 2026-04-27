package com.uberpoc.location.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class DriverLocationEvent {
    private String driverId;
    private double latitude;
    private double longitude;
    private Instant timestamp;
}
