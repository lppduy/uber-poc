package com.uberpoc.location.exception;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(String driverId) {
        super("Driver not found: " + driverId);
    }
}
