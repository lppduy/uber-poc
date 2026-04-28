# Uber POC

Ride-hailing system POC: Java, Spring Boot, Redis GEO, Kafka, WebFlux.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 + WebFlux (reactive) |
| Realtime | Spring WebSocket + STOMP |
| Cache / Geo | Redis 7 (GEO + key-value) |
| Messaging | Apache Kafka (KRaft mode) |
| Database | PostgreSQL 16 + R2DBC (reactive) |
| Metrics | Micrometer + Prometheus + Grafana |
| Logging | Logback + Logstash encoder (JSON, MDC correlationId) |
| Build | Maven 3.9 |
| Container | Docker Compose |

## Services

| Service | Port | Responsibility |
|---|---|---|
| `location-service` | 8081 | Receive driver GPS updates → Redis GEO + Kafka |
| `matching-service` | 8082 | Find nearby drivers, dispatch, handle accept/decline → Kafka |
| `trip-service` | 8083 | Trip state machine → PostgreSQL R2DBC |
| `websocket-gateway` | 8084 | Kafka consumer → STOMP push → rider client |

## Flow

```
Driver App                  Backend                         Rider App
    |                          |                                |
    |── POST /location ────────> location-service              |
    |   (GPS every 3-5s)       | → Redis GEO (for matching)    |
    |                          | → Kafka: driver.location.updated
    |                          |                                |
    |── POST /rides/request ───> matching-service              |
    |   (from rider)           | → query Redis GEO             |
    |                          | → find nearest driver         |
    |                          | → Kafka: ride.matched         |
    |                          |                                |
    |                          | trip-service consumes         |
    |                          | → INSERT trip (MATCHED)       |
    |                          | → Kafka: trip.status.changed  |
    |                          |                                |
    |                          | websocket-gateway consumes    |──> STOMP push ──────> |
    |                          |                               | /topic/trips/{tripId} |
    |                          |                               | status: MATCHED        |
    |                          |                                |
    |── PATCH /trips/{id} ─────> trip-service                 |
    |   status: DRIVER_ARRIVING| → UPDATE PostgreSQL           |
    |   (when arrives pickup)  | → Kafka: trip.status.changed  |
    |                          |                               |──> STOMP push ──────> |
    |                          |                               | status: DRIVER_ARRIVING|
    |                          |                                |
    |── PATCH /trips/{id} ─────> trip-service                 |
    |   status: IN_PROGRESS    | → Kafka: trip.status.changed  |──> STOMP push ──────> |
    |── PATCH /trips/{id} ─────> trip-service                 |
    |   status: COMPLETED      | → Kafka: trip.status.changed  |──> STOMP push ──────> |
```

## Kafka Topics

| Topic | Publisher | Consumer |
|---|---|---|
| `driver.location.updated` | location-service | _(none — data lives in Redis GEO)_ |
| `ride.matched` | matching-service | trip-service |
| `trip.status.changed` | trip-service | websocket-gateway |

## Trip State Machine

```
MATCHED → DRIVER_ARRIVING → IN_PROGRESS → COMPLETED
                                        ↘ CANCELLED
```

Invalid transitions are rejected with HTTP 409.

## Getting Started

### 1. Start infrastructure

```bash
cd infra
docker compose up -d
# postgres:5432  redis:6379  kafka:9092
```

### 2. Run services (each in separate terminal)

```bash
cd services/location-service  && mvn spring-boot:run
cd services/matching-service  && mvn spring-boot:run
cd services/trip-service      && mvn spring-boot:run
cd services/websocket-gateway && mvn spring-boot:run
```

### 3. Run E2E test

```bash
./scripts/e2e-happy-path.sh     # 13 assertions
./scripts/smoke-test.sh         # location-service smoke
./scripts/test-websocket.sh     # websocket-gateway health + STOMP handshake
```

### Optional: Observability stack

```bash
cd infra
docker compose --profile obs up -d
# grafana:3000  prometheus:9090  loki:3100  kafka-ui:8090
```

## Observability

| Tool | URL | Purpose |
|---|---|---|
| Grafana | http://localhost:3000 | Dashboards (metrics + logs) |
| Prometheus | http://localhost:9090 | Metrics scrape |
| Loki | http://localhost:3100 | Log aggregation |
| Kafka UI | http://localhost:8090 | Topic/consumer inspection |

Each service exposes `/actuator/prometheus` and propagates `X-Correlation-ID` through MDC.

## Project Structure

```
uber-poc/
├── infra/
│   ├── docker-compose.yml
│   ├── prometheus.yml
│   └── promtail.yml
├── scripts/
│   ├── e2e-happy-path.sh
│   ├── smoke-test.sh
│   └── test-websocket.sh
└── services/
    ├── location-service/       # port 8081
    ├── matching-service/       # port 8082
    ├── trip-service/           # port 8083
    └── websocket-gateway/      # port 8084
```

## Out of Scope (POC)

- Authentication — mock `userId` in request header
- Real map / routing — mock coordinates
- Payment
- FCM push notification (background)
- Driver location real-time push to rider map
- Horizontal WebSocket scaling (Redis pub/sub relay)
- Kubernetes — Docker Compose only
