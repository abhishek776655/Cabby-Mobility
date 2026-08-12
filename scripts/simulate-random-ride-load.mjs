import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";
import { format as formatUtil } from "node:util";

import {
  buildDefaultSimulationEmails,
  buildStompFrame,
  createRng,
  createRideRequestPayload,
  ensureAuthUserWithRetry,
  handleAcceptedAssignment,
  parseStompFrame
} from "./simulate-ride-scenario.mjs";

const DEFAULT_GATEWAY_URL = "http://localhost:8080";
const DEFAULT_REALTIME_WS_URL = "ws://localhost:8095/ws";
const DEFAULT_PASSWORD = "Passw0rd!123";
const DEFAULT_ACCEPT_PROBABILITY = 0.7;
const DEFAULT_DASHBOARD_REFRESH_MS = 1000;

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

function nextActivePoint(current, pickupLat, pickupLng, stepMeters, rng) {
  const angle = rng() * Math.PI * 2;
  const latOffset = Math.cos(angle) * metersToLatDegrees(stepMeters);
  const lngOffset = Math.sin(angle) * metersToLngDegrees(stepMeters, pickupLat);
  const nextLat = Math.min(90, Math.max(-90, current.lat + latOffset));
  const nextLng = Math.min(180, Math.max(-180, current.lng + lngOffset));

  return {
    lat: round6(nextLat),
    lng: round6(nextLng)
  };
}

function pointAround(rng, centerLat, centerLng, radiusMeters) {
  const angle = rng() * Math.PI * 2;
  const distance = Math.sqrt(rng()) * radiusMeters;
  const latOffset = Math.cos(angle) * metersToLatDegrees(distance);
  const lngOffset = Math.sin(angle) * metersToLngDegrees(distance, centerLat);

  return {
    lat: round6(centerLat + latOffset),
    lng: round6(centerLng + lngOffset)
  };
}

function formatDurationMs(durationMs) {
  const totalSeconds = Math.max(0, Math.floor(durationMs / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }

  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function truncateLine(text, width) {
  const limit = Math.max(0, Number(width) || 0);
  const normalized = String(text ?? "").replace(/\s+/g, " ").trim();

  if (limit <= 0 || normalized.length <= limit) {
    return normalized;
  }

  if (limit <= 1) {
    return normalized.slice(0, limit);
  }

  return `${normalized.slice(0, limit - 1)}…`;
}

function padValue(label, value, width = 14) {
  return `${label.padEnd(width)} ${value}`;
}

export function formatDashboardScreen({
  args,
  stats,
  startedAt,
  now = Date.now(),
  lastEvent = stats.lastEvent || "Waiting for activity..."
}) {
  const width = Math.max(60, Number(process.stdout.columns) || 80);
  const separator = "-".repeat(Math.min(width, 80));
  const lines = [
    "SMART MOBILITY RANDOM LOAD DASHBOARD",
    separator,
    padValue("Run ID:", args.runId),
    padValue("Gateway:", args.gatewayUrl),
    padValue("WebSocket:", args.realtimeWsUrl),
    padValue("Uptime:", formatDurationMs(now - startedAt)),
    separator,
    padValue("Active rides:", stats.activeRides),
    padValue("Active riders:", stats.activeRiders),
    padValue("Active drivers:", stats.activeDrivers),
    padValue("Rides created:", stats.ridesCreated),
    padValue("Completed:", stats.completedRides),
    padValue("Failed:", stats.failedRides),
    padValue("Accepted:", stats.acceptedAssignments),
    padValue("Rejected:", stats.rejectedAssignments),
    separator,
    `Last event: ${truncateLine(lastEvent, Math.max(20, width - 12))}`
  ];

  return lines.join("\n");
}

export function buildRiderProfiles({
  runId,
  riderCount,
  riderPassword = DEFAULT_PASSWORD,
  riderEmail,
  riderEmailPrefix,
  pickupLabel,
  pickupLat,
  pickupLng,
  dropLabel,
  dropLat,
  dropLng,
  radiusMeters,
  seed = 42
}) {
  const rng = createRng(seed);
  const profileRadius = Math.max(120, Math.min(900, Math.round(radiusMeters / 2)));
  const emailPrefix = riderEmailPrefix || `sim.rider.${runId}.`;

  return Array.from({ length: riderCount }, (_unused, index) => {
    const riderIndex = index + 1;
    const pickup = pointAround(rng, pickupLat, pickupLng, profileRadius);
    const drop = pointAround(rng, dropLat, dropLng, profileRadius);

    return {
      index: riderIndex,
      email: riderCount === 1 && riderEmail ? riderEmail : `${emailPrefix}${riderIndex}@example.com`,
      password: riderPassword,
      pickup: {
        label: riderCount === 1 ? pickupLabel : `${pickupLabel} #${riderIndex}`,
        lat: pickup.lat,
        lng: pickup.lng
      },
      drop: {
        label: riderCount === 1 ? dropLabel : `${dropLabel} #${riderIndex}`,
        lat: drop.lat,
        lng: drop.lng
      }
    };
  });
}

export function buildSharedDriverPlan({
  riderProfiles,
  driverCount,
  activeDriverCount,
  radiusMeters,
  seed = 42,
  driverEmailPrefix = `sim.driver.random-load.`,
  driverPassword = DEFAULT_PASSWORD
}) {
  const rng = createRng(seed);
  const drivers = [];
  const riderAnchors = riderProfiles.length > 0 ? riderProfiles : [{
    pickup: { lat: 0, lng: 0 }
  }];

  for (let index = 0; index < driverCount; index += 1) {
    const mode = index < activeDriverCount ? "active" : "idle";
    const anchor = riderAnchors[index % riderAnchors.length];
    const location = pointAround(rng, anchor.pickup.lat, anchor.pickup.lng, radiusMeters);

    drivers.push({
      id: 2001 + index,
      email: `${driverEmailPrefix}${index + 1}@example.com`,
      password: driverPassword,
      mode,
      lat: location.lat,
      lng: location.lng
    });
  }

  return {
    drivers
  };
}

export function createSharedDriverPool(driverAccounts) {
  const state = new Map(driverAccounts.map((driver) => [String(driver.userId), {
    busy: false,
    rideId: null
  }]));

  return {
    isBusy(driverUserId) {
      return Boolean(state.get(String(driverUserId))?.busy);
    },
    tryAcquire(driverUserId, rideId) {
      const key = String(driverUserId);
      const entry = state.get(key);
      if (!entry || entry.busy) {
        return false;
      }

      entry.busy = true;
      entry.rideId = rideId;
      return true;
    },
    release(driverUserId, rideId) {
      const key = String(driverUserId);
      const entry = state.get(key);
      if (!entry) {
        return;
      }

      if (entry.rideId && rideId && entry.rideId !== rideId) {
        return;
      }

      entry.busy = false;
      entry.rideId = null;
    }
  };
}

function safeParseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

function requestJson(url, options = {}) {
  return fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    }
  }).then(async (response) => {
    const raw = await response.text();
    const body = raw ? safeParseJson(raw) : null;

    if (!response.ok) {
      const message = body?.message || body?.error || response.statusText || "Request failed";
      const error = new Error(`${response.status} ${message}`);
      error.status = response.status;
      error.body = body;
      throw error;
    }

    return body;
  });
}

function unwrapData(name, responseBody) {
  if (!responseBody || responseBody.success !== true) {
    const details = responseBody
      ? `status=${responseBody.status ?? "unknown"}, message=${responseBody.message ?? responseBody.error ?? responseBody.raw ?? "unknown"}`
      : "empty response";
    throw new Error(`${name} did not return a successful API response (${details})`);
  }

  if (!responseBody.data) {
    throw new Error(`${name} response is missing data`);
  }

  return responseBody.data;
}

async function createRide({ gatewayUrl, riderToken, payload }) {
  const response = await requestJson(`${gatewayUrl}/rides`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${riderToken}`
    },
    body: JSON.stringify(payload)
  });

  return unwrapData("Ride creation", response);
}

async function getRide({ gatewayUrl, riderToken, rideId }) {
  const response = await requestJson(`${gatewayUrl}/rides/${rideId}`, {
    headers: {
      Authorization: `Bearer ${riderToken}`
    }
  });

  return unwrapData("Ride status", response);
}

async function goDriverOnline({ gatewayUrl, token, driverUserId, lat, lng }) {
  return requestJson(`${gatewayUrl}/location/driver/online`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ driverUserId, lat, lng })
  });
}

async function updateDriverLocation({ gatewayUrl, token, driverUserId, lat, lng }) {
  return requestJson(`${gatewayUrl}/location/driver/update`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ driverUserId, lat, lng })
  });
}

async function sendDriverResponse({ gatewayUrl, token, rideId, dispatchId, driverUserId, accepted = true }) {
  return requestJson(`${gatewayUrl}/dispatch/driver-response`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({
      rideId,
      dispatchId,
      driverUserId,
      response: accepted ? "ACCEPT" : "REJECT"
    })
  });
}

async function waitForDispatchAssignment({
  gatewayUrl,
  realtimeWsUrl,
  rideId,
  riderAccount,
  driverAccounts,
  driverPool,
  rng,
  acceptProbability,
  stats,
  statusTimeoutMs,
  statusPollMs,
  autoStartComplete = true
}) {
  const driverMap = new Map(driverAccounts.map((driver) => [String(driver.userId), driver]));
  // Realtime gateway now requires each driver's own JWT to subscribe to their own
  // /topic/driver/{id} — one WS connection per driver, not one connection watching
  // every candidate's topic, since a single token can't own multiple driver identities.
  const sockets = driverAccounts.map((driver) => ({
    driver,
    topic: `/topic/driver/${driver.userId}`,
    webSocket: new WebSocket(realtimeWsUrl),
    frameBuffer: ""
  }));

  let closed = false;
  let finished = false;
  let messageQueue = Promise.resolve();

  const cleanup = () => {
    if (closed) {
      return;
    }

    closed = true;
    for (const { webSocket } of sockets) {
      try {
        webSocket.close();
      } catch {
        // ignore
      }
    }
  };

  const finalize = (outcome) => {
    if (finished) {
      return;
    }

    finished = true;
    if (outcome.status === "COMPLETED") {
      stats.completedRides += 1;
    } else if (["NO_DRIVER_AVAILABLE", "TIMEOUT", "CLOSED", "CANCELLED"].includes(outcome.status)) {
      stats.failedRides += 1;
    }
    cleanup();
    return outcome;
  };

  const waitForTerminalRideStatus = (async () => {
    const deadline = Date.now() + statusTimeoutMs;

    while (!finished && Date.now() < deadline) {
      let ride;

      try {
        ride = await getRide({ gatewayUrl, riderToken: riderAccount.token, rideId });
      } catch (error) {
        if (error.status === 401 && riderAccount) {
          await refreshAuthAccount(riderAccount, gatewayUrl);
          continue;
        }

        if (error.status !== 404) {
          throw error;
        }

        await delay(statusPollMs);
        continue;
      }

      if (["COMPLETED", "NO_DRIVER_AVAILABLE", "CANCELLED"].includes(ride?.status)) {
        return finalize({ rideId, status: ride.status });
      }

      await delay(statusPollMs);
    }

    if (!finished) {
      return finalize({ rideId, status: "TIMEOUT" });
    }

    return null;
  })();

  const exitPromise = new Promise((resolve, reject) => {
    let closedCount = 0;

    sockets.forEach((socket, index) => {
      const { webSocket, driver, topic } = socket;

      webSocket.addEventListener("open", () => {
        webSocket.send(buildStompFrame("CONNECT", {
          "accept-version": "1.2",
          "heart-beat": "10000,10000",
          Authorization: `Bearer ${driver.token}`
        }));
      });

      webSocket.addEventListener("message", (event) => {
        const chunk = typeof event.data === "string"
          ? event.data
          : Buffer.from(event.data).toString("utf8");
        socket.frameBuffer += chunk;

        const frames = socket.frameBuffer.split("\0");
        socket.frameBuffer = frames.pop() || "";

        for (const frameText of frames) {
          const parsed = parseStompFrame(frameText);
          if (!parsed) {
            continue;
          }

          if (parsed.command === "CONNECTED") {
            webSocket.send(buildStompFrame("SUBSCRIBE", {
              id: `driver-assignment-${rideId}-${index + 1}`,
              destination: topic
            }));
            console.log(`[load] Ride ${rideId}: subscribed to ${topic}`);
            continue;
          }

          if (parsed.command !== "MESSAGE") {
            if (parsed.command === "ERROR") {
              reject(new Error(parsed.body || "WebSocket STOMP error"));
            }
            continue;
          }

          messageQueue = messageQueue.then(async () => {
          if (finished) {
            return;
          }

          const payload = safeParseJson(parsed.body);
          if (payload.rideId !== rideId) {
            return;
          }

          const assignedDriver = driverMap.get(String(payload.driverUserId));
          if (!assignedDriver) {
            console.warn(`[load] Ride ${rideId}: no simulated account for driver ${payload.driverUserId}`);
            return;
          }

          if (driverPool?.isBusy(payload.driverUserId)) {
            stats.rejectedAssignments += 1;
            console.log(`[load] Ride ${rideId}: dispatch ${payload.dispatchId} -> ${assignedDriver.email} => REJECT (busy)`);
            await withAuthRetry({
              gatewayUrl,
              accounts: [assignedDriver],
              label: `Driver response for ${assignedDriver.email}`,
              action: () => sendDriverResponse({
                gatewayUrl,
                token: assignedDriver.token,
                rideId,
                dispatchId: payload.dispatchId,
                driverUserId: payload.driverUserId,
                accepted: false
              })
            });
            return;
          }

          const accepted = shouldAcceptAssignment(rng, acceptProbability);
          console.log(`[load] Ride ${rideId}: dispatch ${payload.dispatchId} -> ${assignedDriver.email} => ${accepted ? "ACCEPT" : "REJECT"}`);

          if (!accepted) {
            stats.rejectedAssignments += 1;
            await withAuthRetry({
              gatewayUrl,
              accounts: [assignedDriver],
              label: `Driver response for ${assignedDriver.email}`,
              action: () => sendDriverResponse({
                gatewayUrl,
                token: assignedDriver.token,
                rideId,
                dispatchId: payload.dispatchId,
                driverUserId: payload.driverUserId,
                accepted: false
              })
            });
            return;
          }

          stats.acceptedAssignments += 1;
          const acquired = driverPool?.tryAcquire(payload.driverUserId, rideId) ?? true;
          if (!acquired) {
            stats.rejectedAssignments += 1;
            console.log(`[load] Ride ${rideId}: dispatch ${payload.dispatchId} -> ${assignedDriver.email} => REJECT (busy acquire)`);
            await withAuthRetry({
              gatewayUrl,
              accounts: [assignedDriver],
              label: `Driver response for ${assignedDriver.email}`,
              action: () => sendDriverResponse({
                gatewayUrl,
                token: assignedDriver.token,
                rideId,
                dispatchId: payload.dispatchId,
                driverUserId: payload.driverUserId,
                accepted: false
              })
            });
            return;
          }

          try {
            await withAuthRetry({
              gatewayUrl,
              accounts: [riderAccount, assignedDriver],
              label: `Ride ${rideId} assignment`,
              action: () => handleAcceptedAssignment({
                gatewayUrl,
                riderToken: riderAccount.token,
                rideId,
                dispatchId: payload.dispatchId,
                driverUserId: payload.driverUserId,
                driverToken: assignedDriver.token,
                autoStartComplete,
                statusTimeoutMs,
                statusPollMs
              })
            });

            resolve(finalize({ rideId, status: "COMPLETED", driverUserId: payload.driverUserId }));
          } finally {
            driverPool?.release(payload.driverUserId, rideId);
          }
        }).catch((error) => {
          reject(error);
        });
        }
      });

      webSocket.addEventListener("close", () => {
        closedCount += 1;
        if (!finished && closedCount === sockets.length) {
          resolve(finalize({ rideId, status: "CLOSED" }));
        }
      });

      webSocket.addEventListener("error", (event) => {
        reject(event.error || new Error("WebSocket connection error"));
      });
    });
  });

  const result = await Promise.race([exitPromise, waitForTerminalRideStatus]);
  cleanup();
  return result;
}

export function createSimulationStats(activeDrivers = 0, activeRiders = 1) {
  return {
    activeRides: 0,
    activeRiders,
    activeDrivers,
    completedRides: 0,
    failedRides: 0,
    rejectedAssignments: 0,
    acceptedAssignments: 0,
    ridesCreated: 0,
    lastEvent: "Waiting for activity..."
  };
}

export function formatLoadSummary(stats) {
  return `[load] activeRides=${stats.activeRides} activeRiders=${stats.activeRiders} activeDrivers=${stats.activeDrivers} completedRides=${stats.completedRides} failedRides=${stats.failedRides} acceptedAssignments=${stats.acceptedAssignments} rejectedAssignments=${stats.rejectedAssignments}`;
}

export function shouldAcceptAssignment(rng, acceptProbability = DEFAULT_ACCEPT_PROBABILITY) {
  return rng() < acceptProbability;
}

export async function runRideLoop({
  createRideCycle,
  shouldStop = () => false,
  maxCycles = Number.POSITIVE_INFINITY,
  rideCooldownMs = 0
}) {
  let cycle = 0;

  while (cycle < maxCycles && !shouldStop()) {
    cycle += 1;
    await createRideCycle({ cycle });

    if (cycle < maxCycles && rideCooldownMs > 0 && !shouldStop()) {
      await delay(rideCooldownMs);
    }
  }
}

async function bootstrapSimulationAccounts({ gatewayUrl, riderProfiles, driverPassword, plan }) {
  const riderAccounts = [];

  for (const riderProfile of riderProfiles) {
    const account = await ensureAuthUserWithRetry({
      gatewayUrl,
      email: riderProfile.email,
      password: riderProfile.password,
      roles: ["RIDER"]
    });

    riderAccounts.push({
      ...riderProfile,
      roles: ["RIDER"],
      password: riderProfile.password,
      token: account.accessToken,
      userId: account.userId
    });
  }

  const driverAccounts = [];
  for (const driver of plan.drivers) {
    const account = await ensureAuthUserWithRetry({
      gatewayUrl,
      email: driver.email,
      password: driverPassword,
      roles: ["DRIVER"]
    });

    driverAccounts.push({
      ...driver,
      roles: ["DRIVER"],
      password: driverPassword,
      token: account.accessToken,
      userId: account.userId
    });
  }

  return {
    riderAccounts,
    driverAccounts
  };
}

function createDashboardRuntime({ enabled, args, stats, startedAt }) {
  if (!enabled || !process.stdout.isTTY) {
    return {
      enabled: false,
      render: () => {},
      record: (message) => {
        stats.lastEvent = String(message ?? stats.lastEvent);
      },
      stop: () => {}
    };
  }

  const originalWrite = process.stdout.write.bind(process.stdout);
  const originalConsole = {
    log: console.log.bind(console),
    warn: console.warn.bind(console),
    error: console.error.bind(console)
  };
  let interval = null;
  let stopped = false;

  const render = () => {
    if (stopped) {
      return;
    }

    const frame = formatDashboardScreen({
      args,
      stats,
      startedAt,
      now: Date.now()
    });

    originalWrite("\x1b[2J\x1b[H");
    originalWrite(`${frame}\n`);
  };

  const record = (message) => {
    stats.lastEvent = truncateLine(message, Math.max(20, (process.stdout.columns || 80) - 12));
  };

  console.log = (...argsToLog) => record(formatUtil(...argsToLog));
  console.warn = (...argsToWarn) => record(`WARN ${formatUtil(...argsToWarn)}`);
  console.error = (...argsToError) => record(`ERROR ${formatUtil(...argsToError)}`);

  originalWrite("\x1b[?25l");
  render();
  interval = setInterval(render, args.dashboardRefreshMs);

  return {
    enabled: true,
    render,
    record,
    stop: () => {
      if (stopped) {
        return;
      }

      stopped = true;
      if (interval) {
        clearInterval(interval);
      }
      console.log = originalConsole.log;
      console.warn = originalConsole.warn;
      console.error = originalConsole.error;
      originalWrite("\x1b[?25h");
    }
  };
}

async function refreshAuthAccount(account, gatewayUrl) {
  if (!account?.email || !account?.password || !Array.isArray(account.roles) || account.roles.length === 0) {
    throw new Error(`Cannot refresh auth for ${account?.email || "unknown account"} without credentials`);
  }

  const refreshed = await ensureAuthUserWithRetry({
    gatewayUrl,
    email: account.email,
    password: account.password,
    roles: account.roles
  });

  account.token = refreshed.accessToken;
  if (Number.isFinite(refreshed.userId)) {
    account.userId = refreshed.userId;
  }

  return account;
}

async function withAuthRetry({ gatewayUrl, accounts = [], label, action }) {
  try {
    return await action();
  } catch (error) {
    if (error?.status !== 401 || accounts.length === 0) {
      throw error;
    }

    const names = accounts.map((account) => account.email).filter(Boolean).join(", ");
    console.warn(`[load] ${label} returned 401 for ${names}; refreshing auth and retrying once.`);

    await Promise.all(accounts.map((account) => refreshAuthAccount(account, gatewayUrl)));
    return action();
  }
}

export function parseArgs(argv) {
  const args = {
    gatewayUrl: process.env.GATEWAY_URL || DEFAULT_GATEWAY_URL,
    realtimeWsUrl: process.env.REALTIME_WS_URL || DEFAULT_REALTIME_WS_URL,
    runId: process.env.SIMULATOR_RUN_ID || "",
    riderEmail: process.env.RIDER_EMAIL || "",
    riderEmailPrefix: process.env.RIDER_EMAIL_PREFIX || "",
    riders: Number(process.env.RIDER_COUNT || 1),
    riderPassword: process.env.RIDER_PASSWORD || DEFAULT_PASSWORD,
    driverPassword: process.env.DRIVER_PASSWORD || DEFAULT_PASSWORD,
    driverCount: Number(process.env.DRIVER_COUNT || 6),
    activeDriverCount: Number(process.env.ACTIVE_DRIVER_COUNT || 3),
    radiusMeters: Number(process.env.RADIUS_METERS || 900),
    seed: Number(process.env.SIMULATOR_SEED || 42),
    updateIntervalMs: Number(process.env.UPDATE_INTERVAL_MS || 3000),
    startupDelayMs: Number(process.env.STARTUP_DELAY_MS || 2000),
    maxWaitMs: Number(process.env.MAX_WAIT_MS || 60_000),
    acceptProbability: Number(process.env.ACCEPT_PROBABILITY || DEFAULT_ACCEPT_PROBABILITY),
    rideCooldownMs: Number(process.env.RIDE_COOLDOWN_MS || 1000),
    summaryIntervalMs: Number(process.env.SUMMARY_INTERVAL_MS || 10_000),
    dashboardRefreshMs: Number(process.env.DASHBOARD_REFRESH_MS || DEFAULT_DASHBOARD_REFRESH_MS),
    dashboard: String(process.env.DASHBOARD_MODE || "false") === "true",
    driverMoveStepMinMeters: Number(process.env.DRIVER_MOVE_STEP_MIN_METERS || 40),
    driverMoveStepMaxMeters: Number(process.env.DRIVER_MOVE_STEP_MAX_METERS || 75),
    pickupLabel: process.env.PICKUP_LABEL || "Connaught Place",
    pickupLat: Number(process.env.PICKUP_LAT || 28.6139),
    pickupLng: Number(process.env.PICKUP_LNG || 77.209),
    dropLabel: process.env.DROP_LABEL || "India Gate",
    dropLat: Number(process.env.DROP_LAT || 28.6129),
    dropLng: Number(process.env.DROP_LNG || 77.2295)
  };

  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const value = argv[index + 1];

    switch (flag) {
      case "--gateway-url":
        args.gatewayUrl = value;
        index += 1;
        break;
      case "--realtime-ws-url":
        args.realtimeWsUrl = value;
        index += 1;
        break;
      case "--run-id":
        args.runId = value;
        index += 1;
        break;
      case "--rider-email":
        args.riderEmail = value;
        index += 1;
        break;
      case "--rider-email-prefix":
        args.riderEmailPrefix = value;
        index += 1;
        break;
      case "--riders":
        args.riders = Number(value);
        index += 1;
        break;
      case "--rider-password":
        args.riderPassword = value;
        index += 1;
        break;
      case "--driver-password":
        args.driverPassword = value;
        index += 1;
        break;
      case "--driver-count":
        args.driverCount = Number(value);
        index += 1;
        break;
      case "--active-driver-count":
        args.activeDriverCount = Number(value);
        index += 1;
        break;
      case "--radius-meters":
        args.radiusMeters = Number(value);
        index += 1;
        break;
      case "--seed":
        args.seed = Number(value);
        index += 1;
        break;
      case "--update-interval-ms":
        args.updateIntervalMs = Number(value);
        index += 1;
        break;
      case "--startup-delay-ms":
        args.startupDelayMs = Number(value);
        index += 1;
        break;
      case "--max-wait-ms":
        args.maxWaitMs = Number(value);
        index += 1;
        break;
      case "--accept-probability":
        args.acceptProbability = Number(value);
        index += 1;
        break;
      case "--ride-cooldown-ms":
        args.rideCooldownMs = Number(value);
        index += 1;
        break;
      case "--summary-interval-ms":
        args.summaryIntervalMs = Number(value);
        index += 1;
        break;
      case "--dashboard-refresh-ms":
        args.dashboardRefreshMs = Number(value);
        index += 1;
        break;
      case "--dashboard":
        args.dashboard = true;
        break;
      case "--driver-move-step-min-meters":
        args.driverMoveStepMinMeters = Number(value);
        index += 1;
        break;
      case "--driver-move-step-max-meters":
        args.driverMoveStepMaxMeters = Number(value);
        index += 1;
        break;
      case "--pickup-label":
        args.pickupLabel = value;
        index += 1;
        break;
      case "--pickup-lat":
        args.pickupLat = Number(value);
        index += 1;
        break;
      case "--pickup-lng":
        args.pickupLng = Number(value);
        index += 1;
        break;
      case "--drop-label":
        args.dropLabel = value;
        index += 1;
        break;
      case "--drop-lat":
        args.dropLat = Number(value);
        index += 1;
        break;
      case "--drop-lng":
        args.dropLng = Number(value);
        index += 1;
        break;
      default:
        throw new Error(`Unknown flag: ${flag}`);
    }
  }

  if (!Number.isFinite(args.driverCount) || args.driverCount <= 0) {
    throw new Error("driverCount must be a positive number");
  }

  if (!Number.isFinite(args.riders) || args.riders <= 0) {
    throw new Error("riders must be a positive number");
  }

  if (!Number.isFinite(args.activeDriverCount) || args.activeDriverCount < 0) {
    throw new Error("activeDriverCount must be zero or a positive number");
  }

  if (args.activeDriverCount > args.driverCount) {
    throw new Error("activeDriverCount cannot be greater than driverCount");
  }

  if (!Number.isFinite(args.acceptProbability) || args.acceptProbability < 0 || args.acceptProbability > 1) {
    throw new Error("acceptProbability must be between 0 and 1");
  }

  if (!Number.isFinite(args.dashboardRefreshMs) || args.dashboardRefreshMs <= 0) {
    throw new Error("dashboardRefreshMs must be a positive number");
  }

  if (!args.runId) {
    args.runId = `random-load-${Date.now().toString(36)}`;
  }

  if (!args.riderEmail) {
    const defaultEmails = buildDefaultSimulationEmails(args.runId);
    args.riderEmail = defaultEmails.riderEmail;
  }

  return args;
}

async function runRandomRideLoad(args) {
  const riderProfiles = buildRiderProfiles({
    runId: args.runId,
    riderCount: args.riders,
    riderPassword: args.riderPassword,
    riderEmail: args.riderEmail,
    riderEmailPrefix: args.riderEmailPrefix,
    pickupLabel: args.pickupLabel,
    pickupLat: args.pickupLat,
    pickupLng: args.pickupLng,
    dropLabel: args.dropLabel,
    dropLat: args.dropLat,
    dropLng: args.dropLng,
    radiusMeters: args.radiusMeters,
    seed: args.seed
  });

  const driverPlan = buildSharedDriverPlan({
    riderProfiles,
    driverCount: args.driverCount,
    activeDriverCount: args.activeDriverCount,
    radiusMeters: args.radiusMeters,
    seed: args.seed,
    driverEmailPrefix: `sim.driver.${args.runId}.`,
    driverPassword: args.driverPassword
  });

  const rng = createRng(args.seed);
  const stats = createSimulationStats(driverPlan.drivers.length, riderProfiles.length);
  const startedAt = Date.now();
  const dashboardRuntime = createDashboardRuntime({
    enabled: args.dashboard,
    args,
    stats,
    startedAt
  });

  console.log("[load] Gateway:", args.gatewayUrl);
  console.log("[load] WebSocket:", args.realtimeWsUrl);
  console.log("[load] Run ID:", args.runId);
  console.log("[load] Riders:", riderProfiles.length);
  console.log("[load] Drivers:", driverPlan.drivers.length, `(${args.activeDriverCount} active, ${args.driverCount - args.activeDriverCount} idle)`);
  console.log("[load] Acceptance probability:", args.acceptProbability);

  let movementTick = 0;
  let summaryTimer = null;
  let updateTimer = null;
  let shutdownRequested = false;

  const shutdown = () => {
    shutdownRequested = true;
    if (summaryTimer) {
      clearInterval(summaryTimer);
      summaryTimer = null;
    }
    if (updateTimer) {
      clearInterval(updateTimer);
      updateTimer = null;
    }
  };

  process.once("SIGINT", shutdown);
  process.once("SIGTERM", shutdown);

  try {
    const { riderAccounts, driverAccounts } = await bootstrapSimulationAccounts({
      gatewayUrl: args.gatewayUrl,
      riderProfiles,
      driverPassword: args.driverPassword,
      plan: driverPlan
    });

    stats.activeDrivers = driverAccounts.length;
    stats.activeRiders = riderAccounts.length;

    for (const riderAccount of riderAccounts) {
      console.log(`[load] Rider ready: ${riderAccount.email} -> user ${riderAccount.userId}`);
    }

    for (const driver of driverAccounts) {
      // driver-service creates the driver profile asynchronously off the "user.created" Kafka
      // event, so a driver can 404 ("Driver not found", surfaced by location-service as a 500)
      // for a moment right after registration. Retry with backoff instead of failing the run.
      const maxOnlineAttempts = 6;
      for (let attempt = 1; attempt <= maxOnlineAttempts; attempt += 1) {
        try {
          await withAuthRetry({
            gatewayUrl: args.gatewayUrl,
            accounts: [driver],
            label: `Driver online for ${driver.email}`,
            action: () => goDriverOnline({
              gatewayUrl: args.gatewayUrl,
              token: driver.token,
              driverUserId: driver.userId,
              lat: driver.lat,
              lng: driver.lng
            })
          });
          break;
        } catch (error) {
          if (attempt === maxOnlineAttempts) {
            throw error;
          }
          console.warn(`[load] Driver online for ${driver.email} not ready yet (attempt ${attempt}/${maxOnlineAttempts}); retrying...`);
          await delay(1000);
        }
      }
      console.log(`[load] Driver online: ${driver.email} -> user ${driver.userId} [${driver.mode}]`);
    }

    const activeDrivers = driverAccounts.filter((driver) => driver.mode === "active");
    const driverPool = createSharedDriverPool(driverAccounts);

    updateTimer = setInterval(async () => {
      if (shutdownRequested) {
        return;
      }

      movementTick += 1;
      const liveRng = createRng(args.seed + movementTick * 1000);

      for (const driver of activeDrivers) {
        const stepMeters = args.driverMoveStepMinMeters + liveRng() * Math.max(0, args.driverMoveStepMaxMeters - args.driverMoveStepMinMeters);
        const updatedPoint = nextActivePoint(
          { lat: driver.lat, lng: driver.lng },
          args.pickupLat,
          args.pickupLng,
          stepMeters,
          liveRng
        );

        driver.lat = updatedPoint.lat;
        driver.lng = updatedPoint.lng;

        try {
          await updateDriverLocation({
            gatewayUrl: args.gatewayUrl,
            token: driver.token,
            driverUserId: driver.userId,
            lat: driver.lat,
            lng: driver.lng
          });
          console.log(`[load] Driver ${driver.userId} moved to (${driver.lat}, ${driver.lng})`);
        } catch (error) {
          if (error?.status === 401) {
            try {
              await refreshAuthAccount(driver, args.gatewayUrl);
              await updateDriverLocation({
                gatewayUrl: args.gatewayUrl,
                token: driver.token,
                driverUserId: driver.userId,
                lat: driver.lat,
                lng: driver.lng
              });
              console.log(`[load] Driver ${driver.userId} moved to (${driver.lat}, ${driver.lng}) after auth refresh`);
              continue;
            } catch (retryError) {
              console.error(`[load] Driver ${driver.userId} update failed after auth refresh: ${retryError.message}`);
              continue;
            }
          }

          console.error(`[load] Driver ${driver.userId} update failed: ${error.message}`);
        }
      }
    }, args.updateIntervalMs);

    if (!args.dashboard) {
      summaryTimer = setInterval(() => {
        console.log(formatLoadSummary(stats));
      }, args.summaryIntervalMs);

      if (summaryTimer.unref) {
        summaryTimer.unref();
      }
    }

    console.log("[load] Warmup complete. Starting continuous rider loops...");

    const riderLoopPromises = riderAccounts.map((riderAccount, riderIndex) => runRideLoop({
      rideCooldownMs: args.rideCooldownMs,
      shouldStop: () => shutdownRequested,
      createRideCycle: async ({ cycle }) => {
        if (shutdownRequested) {
          return;
        }

        stats.activeRides += 1;
        try {
          const riderProfile = riderProfiles[riderIndex];
          const ridePayload = createRideRequestPayload({
            riderUserId: riderAccount.userId,
            pickup: riderProfile.pickup,
            drop: riderProfile.drop
          });

          const ride = await withAuthRetry({
            gatewayUrl: args.gatewayUrl,
            accounts: [riderAccount],
            label: `Ride creation for ${riderAccount.email}`,
            action: () => createRide({
              gatewayUrl: args.gatewayUrl,
              riderToken: riderAccount.token,
              payload: ridePayload
            })
          });

          if (!ride.rideId) {
            throw new Error("Ride response did not include rideId");
          }

          stats.ridesCreated += 1;

          console.log(`[load] Rider ${riderAccount.email} cycle ${cycle}: created ride ${ride.rideId}`);
          console.log(formatLoadSummary(stats));

          const result = await waitForDispatchAssignment({
            gatewayUrl: args.gatewayUrl,
            realtimeWsUrl: args.realtimeWsUrl,
            rideId: ride.rideId,
            riderAccount,
            driverAccounts,
            driverPool,
            rng,
            acceptProbability: args.acceptProbability,
            stats,
            statusTimeoutMs: args.maxWaitMs,
            statusPollMs: Math.max(500, Math.min(5000, Math.floor(args.maxWaitMs / 12))),
            autoStartComplete: true
          });

          if (result?.status === "COMPLETED") {
            console.log(`[load] Rider ${riderAccount.email}: ride completed ${ride.rideId}`);
          } else if (result?.status === "NO_DRIVER_AVAILABLE") {
            console.log(`[load] Rider ${riderAccount.email}: ride failed ${ride.rideId} (no driver available)`);
          } else if (result?.status === "TIMEOUT") {
            console.warn(`[load] Rider ${riderAccount.email}: ride timeout ${ride.rideId}`);
          } else if (result?.status === "CLOSED") {
            console.warn(`[load] Rider ${riderAccount.email}: ride listener closed ${ride.rideId}`);
          }

          console.log(formatLoadSummary(stats));
        } catch (error) {
          stats.failedRides += 1;
          console.error(`[load] Rider ${riderAccount.email} cycle ${cycle} failed: ${error.message}`);
        } finally {
          stats.activeRides = Math.max(0, stats.activeRides - 1);
        }
      }
    }).catch((error) => {
      console.error(`[load] Rider loop for ${riderAccount.email} failed: ${error.message}`);
    }));

    console.log("[load] Continuous ride loop started. Press Ctrl+C to stop.");

    while (!shutdownRequested) {
      await delay(500);
    }

    await Promise.allSettled(riderLoopPromises);
  } finally {
    shutdown();
    process.removeListener("SIGINT", shutdown);
    process.removeListener("SIGTERM", shutdown);
    dashboardRuntime.stop();
    if (args.dashboard) {
      console.log("[load] Final summary:", formatLoadSummary(stats));
    }
  }
}

async function main() {
  const [modeOrFlag, ...rest] = process.argv.slice(2);
  const args = parseArgs(modeOrFlag === "run" ? rest : process.argv.slice(2));
  await runRandomRideLoad(args);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch((error) => {
    console.error("[load] Random ride load failed:", error.message);
    process.exitCode = 1;
  });
}
