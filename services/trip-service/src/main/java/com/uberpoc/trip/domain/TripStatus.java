package com.uberpoc.trip.domain;

import java.util.Set;

public enum TripStatus {
    MATCHED,
    DRIVER_ARRIVING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    // valid next states from each status
    public boolean canTransitionTo(TripStatus next) {
        return switch (this) {
            case MATCHED         -> next == DRIVER_ARRIVING || next == CANCELLED;
            case DRIVER_ARRIVING -> next == IN_PROGRESS     || next == CANCELLED;
            case IN_PROGRESS     -> next == COMPLETED        || next == CANCELLED;
            case COMPLETED, CANCELLED -> false; // terminal states
        };
    }
}
