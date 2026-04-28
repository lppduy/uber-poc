package com.uberpoc.matching.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationEvent {
    private String driverId;
    private double latitude;
    private double longitude;
    private Instant timestamp;
}
