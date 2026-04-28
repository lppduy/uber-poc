#!/usr/bin/env bash
# Smoke test for location-service
# Usage: ./scripts/smoke-test.sh [BASE_URL]
# Example: ./scripts/smoke-test.sh http://localhost:8081

set -euo pipefail

BASE_URL="${1:-http://localhost:8081}"
PASS=0
FAIL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "${GREEN}  ✓ $1${NC}"; ((PASS++)) || true; }
fail() { echo -e "${RED}  ✗ $1${NC}"; ((FAIL++)) || true; }
info() { echo -e "${YELLOW}→ $1${NC}"; }

assert_http() {
  local label="$1" expected="$2" url="$3"
  shift 3
  local actual
  actual=$(curl -s -o /dev/null -w "%{http_code}" "$@" "$url")
  if [[ "$actual" == "$expected" ]]; then
    ok "$label (HTTP $actual)"
  else
    fail "$label — expected HTTP $expected, got HTTP $actual"
  fi
}

assert_body_contains() {
  local label="$1" pattern="$2" url="$3"
  shift 3
  local body
  body=$(curl -s "$@" "$url")
  if echo "$body" | grep -q "$pattern"; then
    ok "$label (body contains '$pattern')"
  else
    fail "$label — body does not contain '$pattern'\n     body: $body"
  fi
}

echo ""
echo "================================================="
echo " Uber POC — location-service smoke test"
echo " Target: $BASE_URL"
echo "================================================="
echo ""

# ── Health ────────────────────────────────────────────
info "1. Actuator health"
assert_http   "GET /actuator/health returns 200" "200" "$BASE_URL/actuator/health"
assert_body_contains "health status is UP" '"status":"UP"' "$BASE_URL/actuator/health"

# ── Update location ───────────────────────────────────
info "2. Update driver location"
assert_http "POST /api/v1/locations/drivers/driver-001 (Hanoi)" "200" \
  "$BASE_URL/api/v1/locations/drivers/driver-001" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"latitude":21.0285,"longitude":105.8542,"status":"AVAILABLE"}'

assert_http "POST /api/v1/locations/drivers/driver-002 (2 km away)" "200" \
  "$BASE_URL/api/v1/locations/drivers/driver-002" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"latitude":21.0385,"longitude":105.8642,"status":"AVAILABLE"}'

assert_http "POST /api/v1/locations/drivers/driver-003 (far away)" "200" \
  "$BASE_URL/api/v1/locations/drivers/driver-003" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"latitude":21.5000,"longitude":105.5000,"status":"AVAILABLE"}'

# ── Nearby drivers ────────────────────────────────────
info "3. Find nearby drivers (radius 5 km from Hanoi center)"
NEARBY=$(curl -s "$BASE_URL/api/v1/locations/drivers/nearby?lat=21.0285&lng=105.8542&radius=5")

if echo "$NEARBY" | grep -q '"driverId"'; then
  ok "GET /api/v1/locations/drivers/nearby returns drivers"
else
  fail "GET /api/v1/locations/drivers/nearby — no drivers found\n     body: $NEARBY"
fi

if echo "$NEARBY" | grep -q '"driver-001"'; then
  ok "driver-001 found in nearby results"
else
  fail "driver-001 not found in nearby results"
fi

if echo "$NEARBY" | grep -q '"driver-002"'; then
  ok "driver-002 found in nearby results"
else
  fail "driver-002 not found in nearby results"
fi

if echo "$NEARBY" | grep -vq '"driver-003"'; then
  ok "driver-003 NOT in results (too far away)"
else
  fail "driver-003 should not appear within 5 km radius"
fi

# ── Validation errors ─────────────────────────────────
info "4. Input validation"
assert_http "Missing body returns 400" "400" \
  "$BASE_URL/api/v1/locations/drivers/driver-x" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{}'

# ── Summary ───────────────────────────────────────────
echo ""
echo "================================================="
TOTAL=$((PASS + FAIL))
if [[ $FAIL -eq 0 ]]; then
  echo -e "${GREEN} All $TOTAL tests passed ✓${NC}"
else
  echo -e "${RED} $FAIL/$TOTAL tests FAILED${NC}"
fi
echo "================================================="
echo ""

exit $FAIL
