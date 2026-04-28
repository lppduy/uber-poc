package com.uberpoc.matching.dto.response;

import com.uberpoc.matching.domain.RideStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MatchResultResponse {
    private String rideId;
    private RideStatus status;
    private String driverId;
    private Double driverLat;
    private Double driverLng;
    private Double distanceKm;
}
