package com.uberpoc.location.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NearbyDriverResponse {
    private String driverId;
    private double latitude;
    private double longitude;
    private double distanceKm;
}
