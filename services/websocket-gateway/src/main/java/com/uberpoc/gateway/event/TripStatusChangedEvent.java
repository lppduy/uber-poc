package com.uberpoc.gateway.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mirror of trip-service's TripStatusChangedEvent.
 * Status fields use String to avoid coupling to trip-service's TripStatus enum.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStatusChangedEvent {
    private String tripId;
    private String riderId;
    private String driverId;
    private String previousStatus;
    private String newStatus;
    private Instant changedAt;
}
