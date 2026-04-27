# Trade-offs - Uber POC

## Redis GEO vs PostGIS cho realtime location

**Chọn Redis GEO**
- Sub-ms read, phù hợp query 200K driver/giây
- In-memory, không persist sau restart (acceptable cho realtime location)
- PostGIS dùng cho trip history (cần ACID, persist)

## WebFlux vs Spring MVC

**Chọn WebFlux**
- Location service là I/O-bound (Redis + Kafka) → reactive phù hợp hơn
- Goroutine equivalent trong Java ecosystem
- Learning goal: reactive programming

## Kafka vs REST call giữa services

**Chọn Kafka cho location events**
- Driver update location 1 lần, nhiều consumer cần biết (matching, WebSocket, analytics)
- Kafka fan-out tự nhiên, không cần gọi từng service
- REST dùng cho synchronous calls (rider đặt xe → matching)
