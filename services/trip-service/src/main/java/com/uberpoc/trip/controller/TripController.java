package com.uberpoc.trip.controller;

import com.uberpoc.trip.common.response.ApiResponse;
import com.uberpoc.trip.domain.TripStatus;
import com.uberpoc.trip.dto.response.TripResponse;
import com.uberpoc.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @GetMapping("/{tripId}")
    public Mono<ResponseEntity<ApiResponse<TripResponse>>> getTrip(@PathVariable String tripId) {
        return tripService.getTrip(tripId)
                .map(trip -> ResponseEntity.ok(ApiResponse.ok(trip)));
    }

    /**
     * Driver or system updates trip status.
     * Valid transitions:
     *   MATCHED → DRIVER_ARRIVING  (driver started heading to pickup)
     *   DRIVER_ARRIVING → IN_PROGRESS  (driver picked up rider)
     *   IN_PROGRESS → COMPLETED  (trip finished)
     *   Any → CANCELLED
     */
    @PutMapping("/{tripId}/status")
    public Mono<ResponseEntity<ApiResponse<TripResponse>>> updateStatus(
            @PathVariable String tripId,
            @RequestParam TripStatus status) {
        return tripService.updateStatus(tripId, status)
                .map(trip -> ResponseEntity.ok(ApiResponse.ok(trip)));
    }
}
