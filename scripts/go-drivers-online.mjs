#!/usr/bin/env node
// Puts N fake drivers online near a coordinate and keeps them moving — nothing else. No rider,
// no ride, no dispatch. Use this instead of simulate-random-ride-load.mjs when you just need
// driver-availability signal for manual app testing; that script's own continuous rider loop
// competes for the same drivers via matchmaking's reservation locks and starves real bookings.
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";
import {
  buildDriverPlan,
  buildStompFrame,
  createRng,
  ensureAuthUserWithRetry,
  parseStompFrame
} from "./simulate-ride-scenario.mjs";

function round6(value) {
  return Number(value.toFixed(6));
}

function metersToLatDegrees(meters) {
  return meters / 111_320;
}

function metersToLngDegrees(meters, lat) {
  const latRadians = (lat * Math.PI) / 180;
  const scale = Math.max(0.00001, Math.cos(latRadians));
  return meters / (111_320 * scale);
}

function nextPoint(current, anchorLat, anchorLng, stepMeters, rng) {
  const angle = rng() * Math.PI * 2;
  const latOffset = Math.cos(angle) * metersToLatDegrees(stepMeters);
  const lngOffset = Math.sin(angle) * metersToLngDegrees(stepMeters, anchorLat);
  return {
    lat: round6(Math.min(90, Math.max(-90, current.lat + latOffset))),
    lng: round6(Math.min(180, Math.max(-180, current.lng + lngOffset)))
  };
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) }
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(body?.message || `${response.status} ${response.statusText}`);
    error.status = response.status;
    throw error;
  }
  return body;
}

async function goOnline({ gatewayUrl, token, driverUserId, lat, lng }) {
  return requestJson(`${gatewayUrl}/location/driver/online`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ driverUserId, lat, lng })
  });
}

async function updateLocation({ gatewayUrl, token, driverUserId, lat, lng }) {
  return requestJson(`${gatewayUrl}/location/driver/update`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ driverUserId, lat, lng })
  });
}

async function sendDriverResponse({ gatewayUrl, token, rideId, dispatchId, driverUserId, accepted = true }) {
  return requestJson(`${gatewayUrl}/dispatch/driver-response`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      rideId,
      dispatchId,
      driverUserId,
      response: accepted ? "ACCEPT" : "REJECT"
    })
  });
}

function safeParseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

// Matchmaking offers assignments over /topic/driver/{driverUserId} — same channel regardless
// of which ride triggered it. Without a driver listening + responding here, every assignment
// sits until DispatchTimeoutScheduler times it out and retries the next candidate, so a ride
// never actually gets ASSIGNED even though drivers show up as "nearby".
function connectDriverSocket({ realtimeWsUrl, driver, gatewayUrl, stopped }) {
  const topic = `/topic/driver/${driver.userId}`;
  let frameBuffer = "";
  const webSocket = new WebSocket(realtimeWsUrl);

  // No auto-reconnect here previously meant a single dropped connection (network blip,
  // server restart) permanently removed that driver from assignment consideration for the
  // rest of the process's life — the process kept running and looked healthy, but silently
  // lost listeners one by one until none were left responding to offers.
  webSocket.addEventListener("close", () => {
    if (stopped.value) return;
    console.warn(`[online] ${driver.email}: socket closed, reconnecting in 2s`);
    setTimeout(() => {
      if (stopped.value) return;
      driver.socket = connectDriverSocket({ realtimeWsUrl, driver, gatewayUrl, stopped });
    }, 2000);
  });

  webSocket.addEventListener("open", () => {
    webSocket.send(buildStompFrame("CONNECT", {
      "accept-version": "1.2",
      "heart-beat": "10000,10000",
      Authorization: `Bearer ${driver.token}`
    }));
  });

  webSocket.addEventListener("message", (event) => {
    const chunk = typeof event.data === "string" ? event.data : Buffer.from(event.data).toString("utf8");
    frameBuffer += chunk;
    const frames = frameBuffer.split("\0");
    frameBuffer = frames.pop() || "";

    for (const frameText of frames) {
      const parsed = parseStompFrame(frameText);
      if (!parsed) continue;

      if (parsed.command === "CONNECTED") {
        webSocket.send(buildStompFrame("SUBSCRIBE", { id: `driver-${driver.userId}`, destination: topic }));
        console.log(`[online] ${driver.email}: listening on ${topic}`);
        continue;
      }

      if (parsed.command !== "MESSAGE") continue;

      const payload = safeParseJson(parsed.body);
      if (!payload.dispatchId) continue;

      console.log(`[online] ${driver.email}: offered ride ${payload.rideId} (dispatch ${payload.dispatchId}) -> ACCEPT`);
      sendDriverResponse({
        gatewayUrl,
        token: driver.token,
        rideId: payload.rideId,
        dispatchId: payload.dispatchId,
        driverUserId: driver.userId,
        accepted: true
      }).catch((error) => {
        console.warn(`[online] ${driver.email}: driver-response failed: ${error.message}`);
      });
    }
  });

  webSocket.addEventListener("error", () => {
    console.warn(`[online] ${driver.email}: socket error, will not auto-reconnect`);
  });

  return webSocket;
}

function parseArgs(argv) {
  const args = {
    gatewayUrl: "http://localhost:8080",
    realtimeWsUrl: "ws://localhost:8095/ws",
    lat: null,
    lng: null,
    driverCount: 5,
    radiusMeters: 1200,
    updateIntervalMs: 3000,
    moveStepMeters: 15,
    seed: 42
  };

  for (let i = 0; i < argv.length; i += 2) {
    const flag = argv[i];
    const value = argv[i + 1];
    switch (flag) {
      case "--gateway-url": args.gatewayUrl = value; break;
      case "--realtime-ws-url": args.realtimeWsUrl = value; break;
      case "--lat": args.lat = Number(value); break;
      case "--lng": args.lng = Number(value); break;
      case "--driver-count": args.driverCount = Number(value); break;
      case "--radius-meters": args.radiusMeters = Number(value); break;
      case "--update-interval-ms": args.updateIntervalMs = Number(value); break;
      case "--seed": args.seed = Number(value); break;
      default:
        throw new Error(`Unknown flag: ${flag}`);
    }
  }

  if (args.lat === null || args.lng === null || Number.isNaN(args.lat) || Number.isNaN(args.lng)) {
    throw new Error("--lat and --lng are required");
  }

  return args;
}

async function main(argv) {
  const args = parseArgs(argv);
  const plan = buildDriverPlan({
    riderLat: args.lat,
    riderLng: args.lng,
    driverCount: args.driverCount,
    activeDriverCount: args.driverCount,
    radiusMeters: args.radiusMeters,
    seed: args.seed,
    driverEmailPrefix: `sim.driver.online-only.${Date.now()}.`
  });

  console.log(`[online] Anchoring ${plan.drivers.length} drivers near (${args.lat}, ${args.lng})`);

  const driverAccounts = [];
  for (const driver of plan.drivers) {
    const account = await ensureAuthUserWithRetry({
      gatewayUrl: args.gatewayUrl,
      email: driver.email,
      password: driver.password,
      roles: ["DRIVER"]
    });
    driverAccounts.push({ ...driver, token: account.accessToken, userId: account.userId });
  }

  for (const driver of driverAccounts) {
    for (let attempt = 1; attempt <= 6; attempt += 1) {
      try {
        await goOnline({
          gatewayUrl: args.gatewayUrl,
          token: driver.token,
          driverUserId: driver.userId,
          lat: driver.lat,
          lng: driver.lng
        });
        break;
      } catch (error) {
        if (attempt === 6) throw error;
        await delay(1000);
      }
    }
    console.log(`[online] Driver online: ${driver.email} -> user ${driver.userId} at (${driver.lat}, ${driver.lng})`);
  }

  const stopped = { value: false };
  for (const driver of driverAccounts) {
    driver.socket = connectDriverSocket({ realtimeWsUrl: args.realtimeWsUrl, driver, gatewayUrl: args.gatewayUrl, stopped });
  }

  console.log("[online] All drivers online and listening for assignments. Ctrl+C to stop.");

  let tick = 0;
  const rng = createRng(args.seed);
  const timer = setInterval(async () => {
    tick += 1;
    for (const driver of driverAccounts) {
      const next = nextPoint(driver, args.lat, args.lng, args.moveStepMeters, rng);
      driver.lat = next.lat;
      driver.lng = next.lng;
      try {
        await updateLocation({
          gatewayUrl: args.gatewayUrl,
          token: driver.token,
          driverUserId: driver.userId,
          lat: driver.lat,
          lng: driver.lng
        });
      } catch (error) {
        console.warn(`[online] Location update failed for ${driver.email}: ${error.message}`);
      }
    }
  }, args.updateIntervalMs);

  const shutdown = () => {
    stopped.value = true;
    clearInterval(timer);
    driverAccounts.forEach((driver) => driver.socket?.close());
    process.exit(0);
  };
  process.once("SIGINT", shutdown);
  process.once("SIGTERM", shutdown);
}

if (fileURLToPath(import.meta.url) === process.argv[1]) {
  main(process.argv.slice(2)).catch((error) => {
    console.error("[online] Failed:", error.message);
    process.exit(1);
  });
}
