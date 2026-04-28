package com.uberpoc.trip.exception;

import com.uberpoc.trip.domain.TripStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(TripStatus from, TripStatus to) {
        super("Cannot transition trip from " + from + " to " + to);
    }
}
