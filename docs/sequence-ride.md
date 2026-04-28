# Sequence Diagram — Happy Path Ride

```mermaid
sequenceDiagram
    participant Driver
    participant LocationSvc as location-service :8081
    participant Redis
    participant Kafka
    participant MatchingSvc as matching-service :8082
    participant TripSvc as trip-service :8083
    participant Rider

    Note over Driver, Redis: Driver comes online

    loop every 3-5s
        Driver->>LocationSvc: POST /api/v1/locations/drivers/{id}
        LocationSvc->>Redis: GEOADD drivers:available lng lat driverId
        LocationSvc->>Kafka: publish driver.location.updated
    end

    Note over Rider, MatchingSvc: Rider books a ride

    Rider->>MatchingSvc: POST /api/v1/rides/request
    MatchingSvc->>Redis: GEORADIUS drivers:available 5km SORT ASC LIMIT 5
    Redis-->>MatchingSvc: [driver-001, driver-002, ...]

    Note over MatchingSvc, Driver: Dispatch to driver-001 (15s timeout)

    MatchingSvc->>Driver: (push notification / WebSocket) ride request
    MatchingSvc->>MatchingSvc: create Sink, wait max 15s

    alt Driver accepts within 15s
        Driver->>MatchingSvc: POST /api/v1/rides/{rideId}/respond ACCEPTED
        MatchingSvc->>MatchingSvc: emit Sink → onDriverAccepted
        MatchingSvc->>Kafka: publish ride.matched
        MatchingSvc-->>Rider: 200 { status: MATCHED, driverId, distanceKm }

        Kafka->>TripSvc: consume ride.matched
        TripSvc->>TripSvc: INSERT trips (status=MATCHED)
        TripSvc->>Redis: SET trip:status:{id} MATCHED EX 3600
        TripSvc->>Kafka: publish trip.status.changed

    else Driver declines or times out
        MatchingSvc->>MatchingSvc: onErrorResume → try driver-002
        Note over MatchingSvc: repeat up to 5 candidates
    end

    Note over Driver, TripSvc: Trip in progress

    Driver->>TripSvc: PUT /api/v1/trips/{id}/status?status=DRIVER_ARRIVING
    TripSvc->>TripSvc: MATCHED → DRIVER_ARRIVING ✓
    TripSvc->>Kafka: publish trip.status.changed

    Driver->>TripSvc: PUT /api/v1/trips/{id}/status?status=IN_PROGRESS
    TripSvc->>TripSvc: DRIVER_ARRIVING → IN_PROGRESS ✓

    Driver->>TripSvc: PUT /api/v1/trips/{id}/status?status=COMPLETED
    TripSvc->>TripSvc: IN_PROGRESS → COMPLETED ✓
    TripSvc->>Kafka: publish trip.status.changed (COMPLETED)
```

## State Machine

```
MATCHED → DRIVER_ARRIVING → IN_PROGRESS → COMPLETED
   ↓              ↓               ↓
CANCELLED      CANCELLED      CANCELLED
```

Terminal states: `COMPLETED`, `CANCELLED` — no further transitions allowed (returns HTTP 409).
