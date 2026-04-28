#!/usr/bin/env bash
# End-to-end happy path test
# Rider requests ride → driver accepts → trip created → status transitions
#
# Usage:
#   ./scripts/e2e-happy-path.sh            # assume services already running
#   ./scripts/e2e-happy-path.sh --start    # start services, run tests, stop after

set -euo pipefail

LOCATION_URL="${LOCATION_URL:-http://localhost:8081}"
MATCHING_URL="${MATCHING_URL:-http://localhost:8082}"
TRIP_URL="${TRIP_URL:-http://localhost:8083}"
MATCHING_LOG="${MATCHING_LOG:-/tmp/matching-svc.log}"
START_SERVICES="${1:-}"

PASS=0; FAIL=0
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'

SERVICE_PIDS=()

# ── Cleanup (runs on EXIT regardless of pass/fail/Ctrl+C) ─────────────────
cleanup() {
  if [[ ${#SERVICE_PIDS[@]} -gt 0 ]]; then
    echo ""
    echo "Stopping services..."
    for pid in "${SERVICE_PIDS[@]}"; do
      kill "$pid" 2>/dev/null || true
    done
    # wait a moment then force kill if still alive
    sleep 2
    for pid in "${SERVICE_PIDS[@]}"; do
      kill -9 "$pid" 2>/dev/null || true
    done
    echo "Services stopped."
  fi
  rm -f "$RIDE_RESPONSE_FILE" 2>/dev/null || true
}

trap cleanup EXIT

# ── Helpers ───────────────────────────────────────────────────────────────
ok()   { echo -e "${GREEN}  ✓ $1${NC}"; ((PASS++)) || true; }
fail() { echo -e "${RED}  ✗ $1${NC}"; ((FAIL++)) || true; }
info() { echo -e "${YELLOW}→ $1${NC}"; }

assert_http() {
  local label="$1" expected="$2" url="$3"; shift 3
  local actual; actual=$(curl -s -o /dev/null -w "%{http_code}" "$@" "$url")
  [[ "$actual" == "$expected" ]] && ok "$label (HTTP $actual)" || fail "$label — got $actual, want $expected"
}

wait_healthy() {
  local url="$1" name="$2"
  for i in {1..30}; do
    if curl -sf "$url/actuator/health" > /dev/null 2>&1; then
      ok "$name ready"
      return 0
    fi
    sleep 2
  done
  fail "$name did not start within 60s"
  return 1
}

RIDE_RESPONSE_FILE=$(mktemp)

# ── Optionally start services ─────────────────────────────────────────────
if [[ "$START_SERVICES" == "--start" ]]; then
  info "Starting all 3 services..."

  # kill anything on these ports first
  lsof -ti:8081,8082,8083 | xargs kill -9 2>/dev/null || true
  sleep 1

  (cd "$(dirname "$0")/../services/location-service" && mvn spring-boot:run -q > /tmp/location-svc.log 2>&1) &
  SERVICE_PIDS+=($!)

  (cd "$(dirname "$0")/../services/matching-service" && mvn spring-boot:run -q > /tmp/matching-svc.log 2>&1) &
  SERVICE_PIDS+=($!)

  (cd "$(dirname "$0")/../services/trip-service" && mvn spring-boot:run -q > /tmp/trip-svc.log 2>&1) &
  SERVICE_PIDS+=($!)

  info "Waiting for services to become healthy..."
  wait_healthy "$LOCATION_URL" "location-service"
  wait_healthy "$MATCHING_URL" "matching-service"
  wait_healthy "$TRIP_URL"     "trip-service"
fi

echo ""
echo "=================================================="
echo " Uber POC — e2e Happy Path"
echo "=================================================="
echo ""

# ── 1. Health checks ──────────────────────────────────
info "1. Health checks"
assert_http "location-service UP" "200" "$LOCATION_URL/actuator/health"
assert_http "matching-service UP" "200" "$MATCHING_URL/actuator/health"
assert_http "trip-service UP"     "200" "$TRIP_URL/actuator/health"

# ── 2. Seed driver locations ──────────────────────────
info "2. Seed driver locations (Hanoi)"

curl -s -X POST "$LOCATION_URL/api/v1/locations/drivers/driver-e2e-01" \
  -H "Content-Type: application/json" \
  -d '{"latitude":21.0285,"longitude":105.8542,"status":"AVAILABLE"}' > /dev/null

curl -s -X POST "$LOCATION_URL/api/v1/locations/drivers/driver-e2e-02" \
  -H "Content-Type: application/json" \
  -d '{"latitude":21.0300,"longitude":105.8560,"status":"AVAILABLE"}' > /dev/null

ok "Seeded 2 drivers in Redis GEO"

# ── 3. Rider requests ride ────────────────────────────
info "3. Rider requests a ride (blocking until driver responds)"

# record current log position so we only grep NEW lines from this test run
LOG_START=$(wc -l < "$MATCHING_LOG" 2>/dev/null || echo 0)

curl -s -X POST "$MATCHING_URL/api/v1/rides/request" \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "rider-e2e-001",
    "pickupLat": 21.0285, "pickupLng": 105.8542,
    "dropoffLat": 21.0500, "dropoffLng": 105.8700
  }' > "$RIDE_RESPONSE_FILE" &

RIDE_REQUEST_PID=$!

# ── 4. Extract rideId from log (retry instead of sleep) ──
info "4. Waiting for dispatch to start..."

RIDE_ID=""
for i in {1..15}; do
  # only look at lines added after this test started
  RIDE_ID=$(tail -n +$((LOG_START + 1)) "$MATCHING_LOG" 2>/dev/null \
              | grep -o 'Ride request rideId=[^ ]*' \
              | tail -1 | cut -d= -f2 || true)
  [[ -n "$RIDE_ID" ]] && break
  sleep 1
done

if [[ -z "$RIDE_ID" ]]; then
  fail "Could not extract rideId from matching-service log after 15s"
  kill $RIDE_REQUEST_PID 2>/dev/null || true
  exit 1
fi

ok "Got rideId=$RIDE_ID"

# ── 5. Driver accepts ─────────────────────────────────
info "5. Driver accepts the ride"

RESPOND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$MATCHING_URL/api/v1/rides/$RIDE_ID/respond" \
  -H "Content-Type: application/json" \
  -d "{\"driverId\":\"driver-e2e-01\",\"response\":\"ACCEPTED\"}")

[[ "$RESPOND_STATUS" == "200" ]] \
  && ok "Driver responded ACCEPTED (HTTP 200)" \
  || fail "Driver respond failed (HTTP $RESPOND_STATUS)"

# ── 6. Wait for /request to complete ─────────────────
info "6. Waiting for ride request to resolve..."
wait $RIDE_REQUEST_PID || true

RIDE_BODY=$(cat "$RIDE_RESPONSE_FILE")

if echo "$RIDE_BODY" | grep -q '"status":"MATCHED"'; then
  ok "Ride matched successfully"
  echo "     rideId:   $(echo "$RIDE_BODY" | grep -o '"rideId":"[^"]*"' | head -1)"
  echo "     driverId: $(echo "$RIDE_BODY" | grep -o '"driverId":"[^"]*"' | head -1)"
else
  fail "Ride match failed — body: $RIDE_BODY"
fi

# ── 7. Verify trip in trip-service (retry for Kafka lag) ─
info "7. Verify trip created in trip-service"

TRIP_BODY=""
for i in {1..10}; do
  TRIP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$TRIP_URL/api/v1/trips/$RIDE_ID")
  if [[ "$TRIP_STATUS" == "200" ]]; then
    TRIP_BODY=$(curl -s "$TRIP_URL/api/v1/trips/$RIDE_ID")
    break
  fi
  sleep 1
done

[[ "$TRIP_STATUS" == "200" ]] \
  && ok "Trip found in trip-service (HTTP 200)" \
  || fail "Trip not found after 10s (HTTP $TRIP_STATUS)"

echo "$TRIP_BODY" | grep -q '"status":"MATCHED"' \
  && ok "Trip status is MATCHED" \
  || fail "Trip status wrong — body: $TRIP_BODY"

# ── 8. State machine transitions ──────────────────────
info "8. Trip state transitions"

S=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$TRIP_URL/api/v1/trips/$RIDE_ID/status?status=DRIVER_ARRIVING")
[[ "$S" == "200" ]] && ok "MATCHED → DRIVER_ARRIVING" || fail "Transition failed (HTTP $S)"

S=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$TRIP_URL/api/v1/trips/$RIDE_ID/status?status=IN_PROGRESS")
[[ "$S" == "200" ]] && ok "DRIVER_ARRIVING → IN_PROGRESS" || fail "Transition failed (HTTP $S)"

S=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$TRIP_URL/api/v1/trips/$RIDE_ID/status?status=COMPLETED")
[[ "$S" == "200" ]] && ok "IN_PROGRESS → COMPLETED" || fail "Transition failed (HTTP $S)"

S=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$TRIP_URL/api/v1/trips/$RIDE_ID/status?status=CANCELLED")
[[ "$S" == "409" ]] && ok "COMPLETED → CANCELLED rejected (HTTP 409)" || fail "Should reject terminal transition (got $S)"

# ── Summary ───────────────────────────────────────────
echo ""
echo "=================================================="
TOTAL=$((PASS + FAIL))
if [[ $FAIL -eq 0 ]]; then
  echo -e "${GREEN} All $TOTAL tests passed ✓${NC}"
else
  echo -e "${RED} $FAIL/$TOTAL tests FAILED${NC}"
fi
echo "=================================================="
echo ""
exit $FAIL
