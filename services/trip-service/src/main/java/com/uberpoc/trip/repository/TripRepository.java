package com.uberpoc.trip.repository;

import com.uberpoc.trip.domain.Trip;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TripRepository extends ReactiveCrudRepository<Trip, String> {
}
