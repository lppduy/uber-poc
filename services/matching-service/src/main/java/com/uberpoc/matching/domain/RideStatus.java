package com.uberpoc.matching.domain;

public enum RideStatus {
    SEARCHING,      // looking for driver
    DISPATCHING,    // sent request to a driver, waiting for response
    MATCHED,        // driver accepted
    NO_DRIVER_FOUND // all candidates declined or timed out
}
