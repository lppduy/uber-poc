# Architecture - Uber POC

## Context

Educational ride-hailing POC focused on real-time location, driver matching, and trip lifecycle.

## Services

- `location-service`: nhận GPS update từ driver, lưu Redis GEO, publish Kafka event
- `matching-service`: tìm driver gần nhất, dispatch ride request, xử lý accept/decline
- `trip-service`: quản lý trip state machine, lưu PostgreSQL
- `notification-service`: push notification mock (FCM)

## Infrastructure

- PostgreSQL + PostGIS: trip history, geospatial route storage
- Redis GEO: driver realtime location (in-memory, sub-ms query)
- Kafka: event-driven communication giữa services
- WebSocket (STOMP): real-time push xuống client

## Data Flow

```
Driver App → location-service → Redis GEO
                              → Kafka (driver.location.updated)
                                    → WebSocket Gateway → Rider App
                                    → matching-service
```

## Next Architecture Milestones

1. Location service: Redis GEO + Kafka publish
2. Matching service: consume location, dispatch to driver
3. Trip service: state machine + PostgreSQL
4. WebSocket gateway: real-time push
