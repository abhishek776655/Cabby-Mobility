#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SIMULATOR="$ROOT_DIR/scripts/simulate-random-ride-load.mjs"

prompt() {
  local label="$1"
  local default_value="$2"
  local reply=""

  read -r -p "$label [$default_value]: " reply
  printf '%s' "${reply:-$default_value}"
}

echo "Smart Mobility random ride load launcher"
echo

GATEWAY_URL="$(prompt "Gateway URL" "http://localhost:8080")"
REALTIME_WS_URL="$(prompt "Realtime WS URL" "ws://localhost:8095/ws")"
RUN_ID="$(prompt "Run ID" "demo-load-$(date +%Y%m%d%H%M%S)")"
RIDERS="$(prompt "Rider count" "4")"
DRIVER_COUNT="$(prompt "Driver count" "12")"
ACTIVE_DRIVER_COUNT="$(prompt "Active driver count" "6")"
ACCEPT_PROBABILITY="$(prompt "Accept probability" "0.7")"
OUTPUT_MODE="$(prompt "Output mode (dashboard/logs)" "dashboard")"
SUMMARY_INTERVAL_MS="$(prompt "Summary interval ms" "1000")"
DASHBOARD_REFRESH_MS="$(prompt "Dashboard refresh ms" "1000")"
RIDE_COOLDOWN_MS="$(prompt "Ride cooldown ms" "1000")"
MAX_WAIT_MS="$(prompt "Max wait ms" "60000")"
UPDATE_INTERVAL_MS="$(prompt "Driver update interval ms" "3000")"
RADIUS_METERS="$(prompt "Radius meters" "900")"
SEED="$(prompt "Seed" "42")"

ARGS=(
  run
  --gateway-url "$GATEWAY_URL"
  --realtime-ws-url "$REALTIME_WS_URL"
  --run-id "$RUN_ID"
  --riders "$RIDERS"
  --driver-count "$DRIVER_COUNT"
  --active-driver-count "$ACTIVE_DRIVER_COUNT"
  --accept-probability "$ACCEPT_PROBABILITY"
  --summary-interval-ms "$SUMMARY_INTERVAL_MS"
  --ride-cooldown-ms "$RIDE_COOLDOWN_MS"
  --max-wait-ms "$MAX_WAIT_MS"
  --update-interval-ms "$UPDATE_INTERVAL_MS"
  --radius-meters "$RADIUS_METERS"
  --seed "$SEED"
)

if [[ "$OUTPUT_MODE" == "dashboard" ]]; then
  ARGS+=(--dashboard --dashboard-refresh-ms "$DASHBOARD_REFRESH_MS")
fi

echo
echo "Launching simulator..."
echo
exec node "$SIMULATOR" "${ARGS[@]}"
