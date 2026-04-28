#!/usr/bin/env bash
# WebSocket Gateway test script
# Tests:
#   1. Health check endpoint
#   2. Kafka -> STOMP broadcast (via wscat or Python fallback)
#
# Prerequisites:
#   - wscat:  npm install -g wscat
#   - OR Python 3 with: pip install websocket-client stomp.py
#
# Usage:
#   ./scripts/test-websocket.sh [GATEWAY_URL] [TRIP_ID]
#
# Examples:
#   ./scripts/test-websocket.sh
#   ./scripts/test-websocket.sh http://localhost:8084 trip-abc-123

set -euo pipefail

GATEWAY_URL="${1:-http://localhost:8084}"
TRIP_ID="${2:-trip-test-$(date +%s)}"
WS_URL="${GATEWAY_URL/http/ws}/ws/websocket"
KAFKA_HOST="${KAFKA_HOST:-localhost:9092}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0

ok()   { echo -e "${GREEN}  ✓ $1${NC}"; ((PASS++)) || true; }
fail() { echo -e "${RED}  ✗ $1${NC}"; ((FAIL++)) || true; }
info() { echo -e "${YELLOW}→ $1${NC}"; }
step() { echo -e "\n${CYAN}── $1 ──${NC}"; }

# ── Helper: HTTP assert ────────────────────────────────────────────────────────
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

# ── 1. Actuator health ─────────────────────────────────────────────────────────
step "1. Actuator health"
assert_http "GET /actuator/health" "200" "${GATEWAY_URL}/actuator/health"

health_body=$(curl -s "${GATEWAY_URL}/actuator/health")
if echo "$health_body" | grep -q '"status":"UP"'; then
  ok "Health status is UP"
else
  fail "Health status not UP — got: $health_body"
fi

# ── 2. Prometheus metrics endpoint ────────────────────────────────────────────
step "2. Prometheus metrics"
assert_http "GET /actuator/prometheus" "200" "${GATEWAY_URL}/actuator/prometheus"

# ── 3. WebSocket connectivity ─────────────────────────────────────────────────
step "3. WebSocket connectivity (SockJS handshake)"

sockjs_info_url="${GATEWAY_URL}/ws/info"
info_status=$(curl -s -o /dev/null -w "%{http_code}" "${sockjs_info_url}")
if [[ "$info_status" == "200" ]]; then
  ok "SockJS /ws/info reachable (HTTP 200)"
else
  fail "SockJS /ws/info — expected 200, got $info_status"
fi

# ── 4. STOMP subscribe + Kafka produce ────────────────────────────────────────
step "4. STOMP subscribe and Kafka event broadcast"

info "Trip ID: $TRIP_ID"
info "Subscribing to STOMP topic: /topic/trips/${TRIP_ID}"

# Try Python-based test if available (most reliable for STOMP)
if command -v python3 &>/dev/null; then
  STOMP_TEST_RESULT=$(python3 - <<PYEOF 2>&1
import sys, json, time, threading
try:
    import websocket
except ImportError:
    print("SKIP:no_websocket_client")
    sys.exit(0)

received = []
ws_url = "${WS_URL}"

def on_message(ws, message):
    received.append(message)

def on_error(ws, error):
    pass

def on_open(ws):
    # STOMP CONNECT frame
    ws.send("CONNECT\naccept-version:1.1,1.2\nheart-beat:0,0\n\n\x00")
    time.sleep(0.5)
    # STOMP SUBSCRIBE frame
    ws.send("SUBSCRIBE\nid:sub-0\ndestination:/topic/trips/${TRIP_ID}\n\n\x00")

ws = websocket.WebSocketApp(ws_url, on_open=on_open, on_message=on_message, on_error=on_error)
t = threading.Thread(target=ws.run_forever)
t.daemon = True
t.start()
time.sleep(2)

# Check if CONNECTED frame arrived
connected = any("CONNECTED" in str(m) for m in received)
print("CONNECTED:" + ("yes" if connected else "no"))
ws.close()
PYEOF
  )

  if echo "$STOMP_TEST_RESULT" | grep -q "SKIP:no_websocket_client"; then
    info "python3 websocket-client not installed — skipping live STOMP test"
    info "Install with: pip install websocket-client"
  elif echo "$STOMP_TEST_RESULT" | grep -q "CONNECTED:yes"; then
    ok "STOMP CONNECT handshake successful"
  else
    fail "STOMP CONNECT failed — output: $STOMP_TEST_RESULT"
  fi
else
  info "python3 not found — skipping live STOMP test"
fi

# ── 5. Kafka produce → STOMP broadcast (manual verification guide) ─────────────
step "5. Kafka produce test event"

KAFKA_EVENT=$(cat <<JSON
{
  "tripId": "${TRIP_ID}",
  "riderId": "rider-001",
  "driverId": "driver-001",
  "previousStatus": "MATCHED",
  "newStatus": "DRIVER_ARRIVING",
  "changedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
JSON
)

info "Producing test event to trip.status.changed ..."

if command -v kafka-console-producer.sh &>/dev/null; then
  echo "$KAFKA_EVENT" | kafka-console-producer.sh \
    --bootstrap-server "$KAFKA_HOST" \
    --topic trip.status.changed \
    --property "parse.key=false" 2>/dev/null
  ok "Event produced via kafka-console-producer.sh"
elif docker exec uber-kafka &>/dev/null 2>&1; then
  echo "$KAFKA_EVENT" | docker exec -i uber-kafka \
    kafka-console-producer --bootstrap-server localhost:9092 \
    --topic trip.status.changed 2>/dev/null
  ok "Event produced via docker exec"
else
  fail "Cannot produce to Kafka — kafka-console-producer.sh or docker not available"
  echo ""
  echo "  Produce manually with:"
  echo "  docker exec -i uber-kafka kafka-console-producer \\"
  echo "    --bootstrap-server localhost:9092 --topic trip.status.changed"
  echo "  Then paste:"
  echo "  $KAFKA_EVENT"
fi

# ── 6. wscat instructions ─────────────────────────────────────────────────────
step "6. Manual WebSocket test with wscat"

echo "  Install wscat:  npm install -g wscat"
echo ""
echo "  Then open TWO terminals:"
echo ""
echo "  Terminal A — subscribe:"
echo "    wscat -c '${GATEWAY_URL/http/ws}/ws/websocket'"
echo "    # After connection, send:"
echo "    CONNECT"
echo "    accept-version:1.2"
echo ""
echo "    (empty line + ^@)"
echo "    SUBSCRIBE"
echo "    id:sub-0"
echo "    destination:/topic/trips/${TRIP_ID}"
echo ""
echo "    (empty line + ^@)"
echo ""
echo "  Terminal B — produce event to Kafka:"
echo "    docker exec -i uber-kafka kafka-console-producer \\"
echo "      --bootstrap-server localhost:9092 --topic trip.status.changed"
echo "    # Then paste JSON:"
echo "    $KAFKA_EVENT"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "────────────────────────────────────"
TOTAL=$((PASS + FAIL))
echo -e "${GREEN}PASS: $PASS${NC} / ${RED}FAIL: $FAIL${NC} / TOTAL: $TOTAL"

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi
