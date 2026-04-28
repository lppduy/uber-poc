package com.uberpoc.matching.exception;

public class NoDriverAvailableException extends RuntimeException {
    public NoDriverAvailableException() {
        super("No available driver found within search radius");
    }
}
