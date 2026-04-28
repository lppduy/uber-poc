package com.uberpoc.trip.event;

import com.uberpoc.trip.domain.TripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStatusChangedEvent {
    private String tripId;
    private String riderId;
    private String driverId;
    private TripStatus previousStatus;
    private TripStatus newStatus;
    private Instant changedAt;
}
