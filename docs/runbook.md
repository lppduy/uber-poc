# Runbook

## Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

## Start Infrastructure

```bash
cd infra
docker compose up -d
```

| Container | Port | Purpose |
|-----------|------|---------|
| uber-postgres | 5432 | Trip persistence (PostgreSQL + PostGIS) |
| uber-redis | 6379 | Driver GEO index + trip state cache |
| uber-kafka | 9092 | Event bus (KRaft, no Zookeeper) |
| uber-kafka-ui | 8090 | Kafka UI — http://localhost:8090 |

Wait ~15s for Kafka to be ready before starting services.

## Start Services

Each in a separate terminal:

```bash
# Terminal 1
cd services/location-service && mvn spring-boot:run

# Terminal 2
cd services/matching-service && mvn spring-boot:run

# Terminal 3
cd services/trip-service && mvn spring-boot:run
```

| Service | Port | Responsibility |
|---------|------|----------------|
| location-service | 8081 | GPS updates, Redis GEO, Kafka producer |
| matching-service | 8082 | Driver dispatch, 15s timeout |
| trip-service | 8083 | Trip lifecycle, PostgreSQL, state machine |

## Health Checks

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## Metrics (Prometheus)

```bash
curl http://localhost:8081/actuator/prometheus | grep location
curl http://localhost:8082/actuator/prometheus | grep matching
curl http://localhost:8083/actuator/prometheus | grep trip
```

Key metrics:

| Metric | Service | Description |
|--------|---------|-------------|
| `location.updates.total` | location | Total GPS updates received |
| `location.nearby.queries.total` | location | Total nearby driver queries |
| `matching.ride.requests.total` | matching | Total ride requests |
| `matching.ride.matched.total` | matching | Successfully matched rides |
| `matching.ride.no_driver.total` | matching | Rides with no driver found |
| `trip.created.total` | trip | Total trips created |
| `trip.completed.total` | trip | Completed trips |
| `trip.cancelled.total` | trip | Cancelled trips |

## Run Tests

```bash
# Smoke test — location-service only
./scripts/smoke-test.sh

# E2E happy path — all 3 services must be running
./scripts/e2e-happy-path.sh

# Start services + test + stop automatically
./scripts/e2e-happy-path.sh --start
```

## Manual Test — Full Flow

```bash
# 1. Update driver location
curl -X POST http://localhost:8081/api/v1/locations/drivers/driver-001 \
  -H "Content-Type: application/json" \
  -d '{"latitude":21.0285,"longitude":105.8542,"status":"AVAILABLE"}'

# 2. Request a ride (runs in background — blocks until driver responds)
curl -X POST http://localhost:8082/api/v1/rides/request \
  -H "Content-Type: application/json" \
  -d '{"riderId":"rider-001","pickupLat":21.0285,"pickupLng":105.8542,"dropoffLat":21.05,"dropoffLng":105.87}' &

# 3. Driver accepts (replace RIDE_ID from matching-service log)
curl -X POST http://localhost:8082/api/v1/rides/RIDE_ID/respond \
  -H "Content-Type: application/json" \
  -d '{"driverId":"driver-001","response":"ACCEPTED"}'

# 4. Check trip
curl http://localhost:8083/api/v1/trips/RIDE_ID

# 5. Progress trip
curl -X PUT "http://localhost:8083/api/v1/trips/RIDE_ID/status?status=DRIVER_ARRIVING"
curl -X PUT "http://localhost:8083/api/v1/trips/RIDE_ID/status?status=IN_PROGRESS"
curl -X PUT "http://localhost:8083/api/v1/trips/RIDE_ID/status?status=COMPLETED"
```

## Correlation ID

Pass `X-Correlation-ID` header to trace a request across services:

```bash
curl -H "X-Correlation-ID: my-trace-123" \
  http://localhost:8081/api/v1/locations/drivers/nearby?lat=21.0&lng=105.8&radius=5
```

The same ID appears in all service logs and is echoed back in the response header.

## Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `driver.location.updated` | location-service | (matching-service, future) | GPS update events |
| `ride.matched` | matching-service | trip-service | Driver accepted, create trip |
| `trip.status.changed` | trip-service | (websocket-gateway, future) | State transition events |

## Stop

```bash
# Stop services: Ctrl+C in each terminal

# Stop infra
cd infra && docker compose down
```

## Logs

Service logs go to stdout. With `--spring.profiles.active=prod`, logs are JSON (Logstash format) for ELK ingestion.

Local (default): readable text with correlationId in brackets.
