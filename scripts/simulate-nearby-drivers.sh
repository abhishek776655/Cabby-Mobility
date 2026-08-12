#!/usr/bin/env bash
# Seeds a handful of drivers online near the rider app's fixed test pickup/drop coordinates
# (src/constants/testLocations.ts in smart-mobility-rider-app) and runs one simulated rider
# through the booking flow, proving nearby-driver availability end to end.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SIMULATOR="$ROOT_DIR/scripts/simulate-random-ride-load.mjs"

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
REALTIME_WS_URL="${REALTIME_WS_URL:-ws://localhost:8095/ws}"
DRIVER_COUNT="${DRIVER_COUNT:-5}"

# Must match src/constants/testLocations.ts exactly — Delhi, matching the NewDelhi.osm.pbf
# Valhalla tiles actually loaded (docker/docker-compose.yml tile_urls).
PICKUP_LABEL="Test Pickup — Connaught Place"
PICKUP_LAT="28.6315"
PICKUP_LNG="77.2167"
DROP_LABEL="Test Drop — Qutub Minar"
DROP_LAT="28.5245"
DROP_LNG="77.1855"

exec node "$SIMULATOR" run \
  --gateway-url "$GATEWAY_URL" \
  --realtime-ws-url "$REALTIME_WS_URL" \
  --run-id "nearby-drivers-demo-$(date +%s)" \
  --riders 1 \
  --driver-count "$DRIVER_COUNT" \
  --active-driver-count "$DRIVER_COUNT" \
  --radius-meters 1200 \
  --pickup-label "$PICKUP_LABEL" \
  --pickup-lat "$PICKUP_LAT" \
  --pickup-lng "$PICKUP_LNG" \
  --drop-label "$DROP_LABEL" \
  --drop-lat "$DROP_LAT" \
  --drop-lng "$DROP_LNG" \
  --accept-probability 0.9 \
  --max-wait-ms 45000 \
  --ride-cooldown-ms 2000
