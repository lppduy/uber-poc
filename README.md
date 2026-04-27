# Uber POC

A ride-hailing system POC inspired by Uber/Grab, built with Java + Spring Boot. Focuses on real-time location tracking, driver matching, and trip lifecycle management.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 + WebFlux (reactive) |
| Realtime | Spring WebSocket + STOMP |
| Cache / Geo | Redis GEO + Redis Pub/Sub |
| Messaging | Apache Kafka |
| Database | PostgreSQL + PostGIS |
| Build | Maven |
| Container | Docker Compose |

## Services

| Service | Port | Responsibility |
|---|---|---|
| `location-service` | 8081 | Receive GPS updates, store Redis GEO, publish Kafka events |
| `matching-service` | 8082 | Find nearby drivers, dispatch ride requests, handle accept/decline |
| `trip-service` | 8083 | Trip state machine, persist to PostgreSQL |
| `websocket-gateway` | 8084 | Push realtime updates to clients (Kafka → WebSocket) |
| `notification-service` | 8085 | FCM push mock |

## Happy Path Flow

```
1. Driver app sends GPS every 3-5s
         ↓
2. location-service → Redis GEO + Kafka (driver.location.updated)
         ↓
3. Rider taps "Book Ride"
         ↓
4. matching-service:
   → Query Redis GEO for top 5 nearest drivers
   → Send ride request to driver #1 (15s timeout)
   → Driver accepts → create Trip
   → Driver declines / timeout → try driver #2
         ↓
5. trip-service creates Trip (status: MATCHED) → PostgreSQL
         ↓
6. WebSocket pushes to rider: "Driver is on the way"
         ↓
7. Driver moves to pickup:
   → location-service receives GPS
   → Kafka → WebSocket gateway → push location to rider every 3s
         ↓
8. Driver taps "Start Trip" → Trip status: IN_PROGRESS
         ↓
9. Driver taps "Complete" → Trip status: COMPLETED
         ↓
10. notification-service sends receipt mock
```

## Data Stores

| Store | Usage | Why |
|---|---|---|
| Redis GEO | Driver realtime location | Sub-ms geospatial query, in-memory |
| PostgreSQL | Trip history, routes | ACID, persistent |
| Kafka | Events between services | Async, fan-out, replay |
| Redis Pub/Sub | Fanout Kafka → WebSocket | Scale WebSocket horizontal |

## Trip State Machine

```
REQUESTED → MATCHED → DRIVER_ARRIVING → IN_PROGRESS → COMPLETED
                                                     ↘ CANCELLED
```

## Out of Scope (POC)

- Real auth → mock `userId` in request header
- Real map/routing → mock coordinates
- Payment
- Mobile app → test with curl + WebSocket client
- Kubernetes → Docker Compose only

## Getting Started

```bash
# Start infrastructure
cd infra && docker compose up -d

# Run location-service
cd services/location-service
mvn spring-boot:run
```

## Project Structure

```
uber-poc/
├── docs/               # Architecture, trade-offs, sequence diagrams
├── local/              # Plan, checklist, build log (local only)
├── infra/              # docker-compose.yml
└── services/
    ├── location-service/
    ├── matching-service/
    ├── trip-service/
    ├── websocket-gateway/
    └── notification-service/
```

## Docs

- [Architecture](docs/architecture.md)
- [Trade-offs](docs/tradeoffs.md)
- [Sequence Diagram](docs/sequence-ride.md) *(coming soon)*
- [Runbook](docs/runbook.md) *(coming soon)*
