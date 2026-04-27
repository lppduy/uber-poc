package com.uberpoc.location.controller;

import com.uberpoc.location.common.response.ApiResponse;
import com.uberpoc.location.dto.request.UpdateLocationRequest;
import com.uberpoc.location.dto.response.NearbyDriverResponse;
import com.uberpoc.location.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PostMapping("/drivers/{driverId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> updateLocation(
            @PathVariable String driverId,
            @Valid @RequestBody UpdateLocationRequest request) {
        return locationService.updateDriverLocation(driverId, request)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.<Void>ok("location updated", null))));
    }

    @GetMapping("/drivers/nearby")
    public Mono<ResponseEntity<ApiResponse<List<NearbyDriverResponse>>>> getNearbyDrivers(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radius) {
        return locationService.findNearbyDrivers(lat, lng, radius)
                .collectList()
                .map(drivers -> ResponseEntity.ok(ApiResponse.ok(drivers)));
    }
}
