package com.uberpoc.matching.controller;

import com.uberpoc.matching.common.response.ApiResponse;
import com.uberpoc.matching.dto.request.DriverRespondDto;
import com.uberpoc.matching.dto.request.RideRequestDto;
import com.uberpoc.matching.dto.response.MatchResultResponse;
import com.uberpoc.matching.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    /**
     * Rider requests a ride.
     * Blocks reactively until a driver accepts (or all candidates decline/timeout).
     */
    @PostMapping("/request")
    public Mono<ResponseEntity<ApiResponse<MatchResultResponse>>> requestRide(
            @Valid @RequestBody RideRequestDto request) {
        return matchingService.requestRide(request)
                .map(result -> ResponseEntity.ok(ApiResponse.ok(result)));
    }

    /**
     * Driver accepts or declines a dispatched ride request.
     */
    @PostMapping("/{rideId}/respond")
    public Mono<ResponseEntity<ApiResponse<Void>>> driverRespond(
            @PathVariable String rideId,
            @Valid @RequestBody DriverRespondDto dto) {
        return matchingService.driverRespond(rideId, dto.getDriverId(), dto.getResponse())
                .then(Mono.just(ResponseEntity.ok(ApiResponse.<Void>ok("response recorded", null))));
    }
}
