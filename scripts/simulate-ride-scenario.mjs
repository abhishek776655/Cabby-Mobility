import * as readline from "node:readline";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";

const DEFAULT_GATEWAY_URL = "http://localhost:8080";
const DEFAULT_REALTIME_WS_URL = "ws://localhost:8095/ws";
const DEFAULT_PASSWORD = "Passw0rd!123";
const DEFAULT_RIDER_EMAIL_PREFIX = "sim.rider";
const DEFAULT_DRIVER_EMAIL_PREFIX = "sim.driver";
const DEFAULT_DRIVER_INDEX = 1;
const DEFAULT_SESSION_FILE = process.env.SIMULATOR_SESSION_FILE || path.join(os.tmpdir(), "smart-mobility-sim-session.json");
const SESSION_TTL_MS = 30 * 60 * 1000;

export function createRng(seed = 42) {
  let state = seed >>> 0;

  return function next() {
    state = (1664525 * state + 1013904223) >>> 0;
    return state / 4294967296;
  };
}

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

export function buildDriverPlan({
  riderLat,
  riderLng,
  driverCount,
  activeDriverCount,
  radiusMeters,
  seed = 42,
  driverEmailPrefix = DEFAULT_DRIVER_EMAIL_PREFIX,
  driverPassword = DEFAULT_PASSWORD
}) {
  const rng = createRng(seed);
  const drivers = [];

  for (let index = 0; index < driverCount; index += 1) {
    const mode = index < activeDriverCount ? "active" : "idle";
    const location = pointAround(rng, riderLat, riderLng, radiusMeters);

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
    rider: {
      lat: riderLat,
      lng: riderLng
    },
    drivers
  };
}

export function buildDefaultSimulationEmails(runId) {
  return {
    riderEmail: `${DEFAULT_RIDER_EMAIL_PREFIX}.${runId}@example.com`,
    driverEmailPrefix: `${DEFAULT_DRIVER_EMAIL_PREFIX}.${runId}.`
  };
}

function readSimulationSession(sessionFile) {
  try {
    const raw = fs.readFileSync(sessionFile, "utf8");
    const parsed = JSON.parse(raw);
    if (typeof parsed === "string" && parsed.trim()) {
      return parsed.trim();
    }
    if (parsed && typeof parsed.runId === "string" && parsed.runId.trim()) {
      return parsed.runId.trim();
    }
  } catch {
    // ignore missing or malformed session files
  }

  return null;
}

function readSimulationSessionData(sessionFile) {
  try {
    const raw = fs.readFileSync(sessionFile, "utf8");
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed;
    }
  } catch {
    // ignore missing or malformed session files
  }

  return null;
}

function normalizeDriverEmails(driverEmails) {
  if (!Array.isArray(driverEmails)) {
    return [];
  }

  return driverEmails
    .filter((email) => typeof email === "string" && email.trim())
    .map((email) => email.trim());
}

function writeSimulationSession(sessionFile, session) {
  try {
    fs.writeFileSync(
      sessionFile,
      JSON.stringify(
        typeof session === "string"
          ? {
              runId: session,
              updatedAt: new Date().toISOString()
            }
          : {
              ...session,
              updatedAt: new Date().toISOString()
            },
        null,
        2
      )
    );
  } catch {
    // ignore session file write failures
  }
}

function resolveSimulationRunId(sessionFile, explicitRunId) {
  if (explicitRunId) {
    writeSimulationSession(sessionFile, explicitRunId);
    return explicitRunId;
  }

  try {
    const stat = fs.statSync(sessionFile);
    if (Date.now() - stat.mtimeMs <= SESSION_TTL_MS) {
      const existing = readSimulationSession(sessionFile);
      if (existing) {
        return existing;
      }
    }
  } catch {
    // ignore missing session files
  }

  const generated = Date.now().toString(36);
  writeSimulationSession(sessionFile, generated);
  return generated;
}

function buildDriverAccountsFromSession(sessionData, fallbackPlan) {
  const driverEmails = normalizeDriverEmails(sessionData?.driverEmails);
  if (driverEmails.length > 0) {
    return driverEmails.map((email, index) => ({
      id: 2001 + index,
      email,
      password: sessionData?.driverPassword || DEFAULT_PASSWORD,
      mode: index < Number(sessionData?.activeDriverCount || 0) ? "active" : "idle",
      lat: Number(sessionData?.driverLocations?.[index]?.lat ?? fallbackPlan.drivers[index]?.lat ?? sessionData?.pickupLat ?? fallbackPlan.rider.lat),
      lng: Number(sessionData?.driverLocations?.[index]?.lng ?? fallbackPlan.drivers[index]?.lng ?? sessionData?.pickupLng ?? fallbackPlan.rider.lng)
    }));
  }

  return fallbackPlan.drivers;
}

export function createRideRequestPayload({ riderUserId, pickup, drop, vehicleType = "STANDARD" }) {
  return {
    riderUserId,
    pickupLocation: pickup.label,
    dropLocation: drop.label,
    pickupLatitude: pickup.lat,
    pickupLongitude: pickup.lng,
    dropLatitude: drop.lat,
    dropLongitude: drop.lng,
    vehicleType
  };
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    }
  });

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
}

function safeParseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

export function buildStompFrame(command, headers = {}, body = "") {
  const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
  const lines = [command, ...headerLines, "", body];
  return `${lines.join("\n")}\0`;
}

export function parseStompFrame(rawFrame) {
  const text = typeof rawFrame === "string" ? rawFrame : Buffer.from(rawFrame).toString("utf8");
  const frame = text.replace(/\0+$/g, "").replace(/\r\n/g, "\n");

  if (!frame.trim()) {
    return null;
  }

  const separatorIndex = frame.indexOf("\n\n");
  const head = separatorIndex === -1 ? frame : frame.slice(0, separatorIndex);
  const body = separatorIndex === -1 ? "" : frame.slice(separatorIndex + 2);
  const [commandLine, ...headerLines] = head.split("\n");
  const headers = {};

  for (const line of headerLines) {
    if (!line) {
      continue;
    }

    const colonIndex = line.indexOf(":");
    if (colonIndex === -1) {
      continue;
    }

    headers[line.slice(0, colonIndex)] = line.slice(colonIndex + 1);
  }

  return {
    command: commandLine.trim(),
    headers,
    body
  };
}

export function formatRemainingTime(expiresAt, nowMs = Date.now()) {
  const expiresAtMs = typeof expiresAt === "number"
    ? expiresAt
    : new Date(expiresAt).getTime();

  if (!Number.isFinite(expiresAtMs)) {
    return "00:00";
  }

  const remainingMs = Math.max(0, expiresAtMs - nowMs);
  const totalSeconds = Math.ceil(remainingMs / 1000);
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, "0");
  const seconds = String(totalSeconds % 60).padStart(2, "0");
  return `${minutes}:${seconds}`;
}

export function formatCountdownBar(expiresAt, nowMs = Date.now(), width = 20, totalSeconds = 30) {
  const expiresAtMs = typeof expiresAt === "number"
    ? expiresAt
    : new Date(expiresAt).getTime();

  if (!Number.isFinite(expiresAtMs) || width <= 0) {
    return "[]";
  }

  const remainingSeconds = Math.max(0, Math.ceil((expiresAtMs - nowMs) / 1000));
  const progress = Math.min(1, Math.max(0, remainingSeconds / Math.max(1, totalSeconds)));
  const filled = Math.max(0, Math.min(width, Math.round(width * progress)));
  const empty = width - filled;
  return `[${"█".repeat(filled)}${"░".repeat(empty)}]`;
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

async function retryOperation(operation, {
  retries = 4,
  delayMs = 1000,
  label = "operation"
} = {}) {
  let lastError = null;

  for (let attempt = 1; attempt <= retries; attempt += 1) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      if (attempt < retries) {
        await delay(delayMs);
      }
    }
  }

  throw new Error(`${label} failed after ${retries} attempts: ${lastError?.message || "unknown error"}`);
}

async function ensureAuthUser({ gatewayUrl, email, password, roles }) {
  try {
    const loginResponse = await requestJson(`${gatewayUrl}/auth/login`, {
      method: "POST",
      body: JSON.stringify({ email, password })
    });
    return unwrapData("Login", loginResponse);
  } catch (loginError) {
    try {
      const registerResponse = await requestJson(`${gatewayUrl}/auth/register`, {
        method: "POST",
        body: JSON.stringify({ email, password, roles })
      });
      return unwrapData("Register", registerResponse);
    } catch (registerError) {
      if (registerError?.status === 409) {
        const retryLoginResponse = await requestJson(`${gatewayUrl}/auth/login`, {
          method: "POST",
          body: JSON.stringify({ email, password })
        });
        return unwrapData("Login", retryLoginResponse);
      }

      throw registerError;
    }
  }
}

async function bootstrapSimulationAccounts(args, plan) {
  const rider = await ensureAuthUserWithRetry({
    gatewayUrl: args.gatewayUrl,
    email: args.riderEmail,
    password: args.riderPassword,
    roles: ["RIDER"]
  });

  const driverAccounts = await createDriverAccounts({
    gatewayUrl: args.gatewayUrl,
    plan
  });

  writeSimulationSession(args.sessionFile, {
    runId: args.runId,
    riderEmail: args.riderEmail,
    riderUserId: rider.userId,
    riderAccessToken: rider.accessToken,
    riderRoles: rider.roles,
    driverEmailPrefix: args.driverEmailPrefix,
    driverEmails: driverAccounts.map((driver) => driver.email),
    driverAccounts: driverAccounts.map((driver) => ({
      email: driver.email,
      userId: driver.userId,
      accessToken: driver.token,
      mode: driver.mode,
      lat: driver.lat,
      lng: driver.lng
    })),
    driverCount: args.driverCount,
    activeDriverCount: args.activeDriverCount,
    radiusMeters: args.radiusMeters,
    seed: args.seed,
    updateIntervalMs: args.updateIntervalMs,
    startupDelayMs: args.startupDelayMs,
    maxWaitMs: args.maxWaitMs,
    pickupLabel: args.pickupLabel,
    pickupLat: args.pickupLat,
    pickupLng: args.pickupLng,
    dropLabel: args.dropLabel,
    dropLat: args.dropLat,
    dropLng: args.dropLng
  });

  return {
    rider,
    driverAccounts
  };
}

export function loadSimulationAccountsFromSession(args, plan) {
  const sessionData = readSimulationSessionData(args.sessionFile);
  if (sessionData?.runId !== args.runId) {
    return null;
  }

  const riderUserId = Number(sessionData?.riderUserId);
  const riderAccessToken = typeof sessionData?.riderAccessToken === "string" ? sessionData.riderAccessToken.trim() : "";
  const riderRoles = Array.isArray(sessionData?.riderRoles) && sessionData.riderRoles.length > 0
    ? sessionData.riderRoles.filter((role) => typeof role === "string" && role.trim()).map((role) => role.trim())
    : ["RIDER"];
  const storedDrivers = Array.isArray(sessionData?.driverAccounts) ? sessionData.driverAccounts : [];

  if (!riderAccessToken || !Number.isFinite(riderUserId) || storedDrivers.length !== plan.drivers.length) {
    return null;
  }

  const driverAccounts = plan.drivers.map((driver, index) => {
    const storedDriver = storedDrivers[index] || {};
    const driverUserId = Number(storedDriver.userId);
    const accessToken = typeof storedDriver.accessToken === "string" ? storedDriver.accessToken.trim() : "";

    if (!accessToken || !Number.isFinite(driverUserId)) {
      return null;
    }

    return {
      ...driver,
      token: accessToken,
      userId: driverUserId
    };
  });

  if (driverAccounts.some((driver) => driver == null)) {
    return null;
  }

  return {
    rider: {
      userId: riderUserId,
      accessToken: riderAccessToken,
      roles: riderRoles
    },
    driverAccounts,
    reusedWarmupSession: true
  };
}

async function resolveSimulationAccounts(args, plan) {
  const cachedAccounts = loadSimulationAccountsFromSession(args, plan);
  if (cachedAccounts) {
    return cachedAccounts;
  }

  return {
    ...(await bootstrapSimulationAccounts(args, plan)),
    reusedWarmupSession: false
  };
}

export async function ensureAuthUserWithRetry({
  gatewayUrl,
  email,
  password,
  roles,
  retries = 6,
  delayMs = 1500
}) {
  return retryOperation(
    () => ensureAuthUser({ gatewayUrl, email, password, roles }),
    {
      retries,
      delayMs,
      label: `Auth bootstrap for ${email}`
    }
  );
}

async function createDriverAccounts({ gatewayUrl, plan }) {
  const driverAccounts = [];

  for (const driver of plan.drivers) {
    const account = await ensureAuthUserWithRetry({
      gatewayUrl,
      email: driver.email,
      password: driver.password,
      roles: ["DRIVER"]
    });

    driverAccounts.push({
      ...driver,
      token: account.accessToken,
      userId: account.userId
    });
  }

  return driverAccounts;
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

async function goDriverOnline({ gatewayUrl, token, driverUserId, lat, lng }) {
  return requestJson(`${gatewayUrl}/location/driver/online`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ driverUserId, lat, lng })
  });
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

export async function waitForRideStatus({
  gatewayUrl,
  riderToken,
  rideId,
  expectedStatus,
  timeoutMs = 60_000,
  intervalMs = 2_000
}) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const ride = await getRide({ gatewayUrl, riderToken, rideId });
    if (ride?.status === expectedStatus) {
      return ride;
    }

    await delay(intervalMs);
  }

  throw new Error(`Timed out waiting for ride ${rideId} to reach ${expectedStatus}`);
}

export async function startRide({ gatewayUrl, driverToken, rideId }) {
  const response = await requestJson(`${gatewayUrl}/rides/${rideId}/start`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${driverToken}`
    }
  });

  return unwrapData("Start ride", response);
}

export async function completeRide({ gatewayUrl, driverToken, rideId }) {
  const response = await requestJson(`${gatewayUrl}/rides/${rideId}/complete`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${driverToken}`
    }
  });

  return unwrapData("Complete ride", response);
}

async function pollDispatch({ gatewayUrl, riderToken, rideId, timeoutMs = 60_000, intervalMs = 2_000 }) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    try {
      const response = await requestJson(`${gatewayUrl}/dispatch/${rideId}`, {
        headers: {
          Authorization: `Bearer ${riderToken}`
        }
      });
      const data = response?.data;
      if (data && data.dispatchId && data.driverUserId != null) {
        return data;
      }
    } catch (error) {
      if (error.status !== 404) {
        throw error;
      }
    }

    await delay(intervalMs);
  }

  throw new Error(`Timed out waiting for dispatch assignment for ride ${rideId}`);
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

export async function handleAcceptedAssignment({
  gatewayUrl,
  riderToken,
  rideId,
  dispatchId,
  driverUserId,
  driverToken,
  autoStartComplete = false,
  statusTimeoutMs = 60_000,
  statusPollMs = 2_000,
  completionDelayMs = 5_000
}) {
  await sendDriverResponse({
    gatewayUrl,
    token: driverToken,
    rideId,
    dispatchId,
    driverUserId,
    accepted: true
  });
  console.log(`[listen] Sent ACCEPT for dispatch ${dispatchId}`);

  if (!autoStartComplete) {
    return;
  }

  console.log(`[listen] Waiting for ride ${rideId} to become DRIVER_ASSIGNED...`);
  await waitForRideStatus({
    gatewayUrl,
    riderToken,
    rideId,
    expectedStatus: "DRIVER_ASSIGNED",
    timeoutMs: statusTimeoutMs,
    intervalMs: statusPollMs
  });

  console.log(`[listen] Starting ride ${rideId}...`);
  await startRide({
    gatewayUrl,
    driverToken,
    rideId
  });

  console.log(`[listen] Waiting for ride ${rideId} to become ONGOING...`);
  await waitForRideStatus({
    gatewayUrl,
    riderToken,
    rideId,
    expectedStatus: "ONGOING",
    timeoutMs: statusTimeoutMs,
    intervalMs: statusPollMs
  });

  console.log(`[listen] Ride ongoing. Completing in 5 seconds...`);
  if (completionDelayMs > 0) {
    await delay(completionDelayMs);
  }

  await completeRide({
    gatewayUrl,
    driverToken,
    rideId
  });

  console.log(`[listen] Ride completed for ${rideId}`);
}

function normalizeModeAndArgs(argv) {
  if (argv[0] === "run" || argv[0] === "listen" || argv[0] === "warmup") {
    return { mode: argv[0], args: argv.slice(1) };
  }

  return { mode: "run", args: argv };
}

function parseArgs(argv, mode = "run") {
  const sessionData = readSimulationSessionData(process.env.SIMULATOR_SESSION_FILE || DEFAULT_SESSION_FILE) || {};
  const args = {
    gatewayUrl: process.env.GATEWAY_URL || DEFAULT_GATEWAY_URL,
    realtimeWsUrl: process.env.REALTIME_WS_URL || DEFAULT_REALTIME_WS_URL,
    runId: process.env.SIMULATOR_RUN_ID || sessionData.runId || "",
    riderEmail: process.env.RIDER_EMAIL || sessionData.riderEmail || "",
    riderPassword: process.env.RIDER_PASSWORD || DEFAULT_PASSWORD,
    driverPassword: process.env.DRIVER_PASSWORD || DEFAULT_PASSWORD,
    driverEmail: process.env.DRIVER_EMAIL || "",
    driverIndex: Number(process.env.DRIVER_INDEX || DEFAULT_DRIVER_INDEX),
    driverCount: Number(process.env.DRIVER_COUNT || sessionData.driverCount || 6),
    activeDriverCount: Number(process.env.ACTIVE_DRIVER_COUNT || sessionData.activeDriverCount || 3),
    radiusMeters: Number(process.env.RADIUS_METERS || sessionData.radiusMeters || 900),
    seed: Number(process.env.SIMULATOR_SEED || sessionData.seed || 42),
    updateIntervalMs: Number(process.env.UPDATE_INTERVAL_MS || sessionData.updateIntervalMs || 3000),
    startupDelayMs: Number(process.env.STARTUP_DELAY_MS || sessionData.startupDelayMs || 4000),
    maxWaitMs: Number(process.env.MAX_WAIT_MS || sessionData.maxWaitMs || 60000),
    happyPath: String(process.env.SIMULATOR_HAPPY_PATH || sessionData.happyPath || "false") === "true",
    pickupLabel: process.env.PICKUP_LABEL || sessionData.pickupLabel || "Connaught Place",
    pickupLat: Number(process.env.PICKUP_LAT || sessionData.pickupLat || 28.6139),
    pickupLng: Number(process.env.PICKUP_LNG || sessionData.pickupLng || 77.209),
    dropLabel: process.env.DROP_LABEL || sessionData.dropLabel || "India Gate",
    dropLat: Number(process.env.DROP_LAT || sessionData.dropLat || 28.6129),
    dropLng: Number(process.env.DROP_LNG || sessionData.dropLng || 77.2295),
    driverEmailPrefix: process.env.DRIVER_EMAIL_PREFIX || sessionData.driverEmailPrefix || "",
    sessionFile: process.env.SIMULATOR_SESSION_FILE || DEFAULT_SESSION_FILE,
    mode
  };
  let riderEmailExplicit = Boolean(process.env.RIDER_EMAIL);
  let driverEmailPrefixExplicit = Boolean(process.env.DRIVER_EMAIL_PREFIX);
  let runIdExplicit = Boolean(process.env.SIMULATOR_RUN_ID);

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
      case "--rider-email":
        args.riderEmail = value;
        riderEmailExplicit = true;
        index += 1;
        break;
      case "--run-id":
        args.runId = value;
        runIdExplicit = true;
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
      case "--driver-email":
        args.driverEmail = value;
        index += 1;
        break;
      case "--driver-index":
        args.driverIndex = Number(value);
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
      case "--max-wait-ms":
        args.maxWaitMs = Number(value);
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
      case "--driver-email-prefix":
        args.driverEmailPrefix = value;
        driverEmailPrefixExplicit = true;
        index += 1;
        break;
      case "--startup-delay-ms":
        args.startupDelayMs = Number(value);
        index += 1;
        break;
      case "--happy-path":
        if (value === undefined || value.startsWith("--")) {
          args.happyPath = true;
        } else {
          args.happyPath = value !== "false";
          index += 1;
        }
        break;
      default:
        throw new Error(`Unknown flag: ${flag}`);
    }
  }

  if (!Number.isFinite(args.driverCount) || args.driverCount <= 0) {
    throw new Error("driverCount must be a positive number");
  }

  if (!Number.isFinite(args.activeDriverCount) || args.activeDriverCount < 0) {
    throw new Error("activeDriverCount must be zero or a positive number");
  }

  if (args.activeDriverCount > args.driverCount) {
    throw new Error("activeDriverCount cannot be greater than driverCount");
  }

  args.runId = resolveSimulationRunId(args.sessionFile, runIdExplicit ? args.runId : args.runId || "");
  const defaultEmails = buildDefaultSimulationEmails(args.runId);

  if (!riderEmailExplicit) {
    args.riderEmail = defaultEmails.riderEmail;
  }

  if (!driverEmailPrefixExplicit) {
    args.driverEmailPrefix = defaultEmails.driverEmailPrefix;
  }

  const existingSessionData = readSimulationSessionData(args.sessionFile) || {};

  writeSimulationSession(args.sessionFile, {
    ...existingSessionData,
    runId: args.runId,
    riderEmail: args.riderEmail,
    driverEmailPrefix: args.driverEmailPrefix,
    driverCount: args.driverCount,
    activeDriverCount: args.activeDriverCount,
    radiusMeters: args.radiusMeters,
    seed: args.seed,
    updateIntervalMs: args.updateIntervalMs,
    startupDelayMs: args.startupDelayMs,
    maxWaitMs: args.maxWaitMs,
    happyPath: args.happyPath,
    pickupLabel: args.pickupLabel,
    pickupLat: args.pickupLat,
    pickupLng: args.pickupLng,
    dropLabel: args.dropLabel,
    dropLat: args.dropLat,
    dropLng: args.dropLng
  });

  return args;
}

function resolveDriverEmail(args) {
  if (args.driverEmail) {
    return args.driverEmail;
  }

  return `${args.driverEmailPrefix}${args.driverIndex}@example.com`;
}

async function askForDriverDecision(dispatchId, driverUserId, driverLabel = "", expiresAt = null) {
  const promptText = `[listen] Dispatch ${dispatchId} for driver ${driverUserId}${driverLabel ? ` (${driverLabel})` : ""}. Type accept or reject: `;

  if (!process.stdin.isTTY || !process.stdout.isTTY) {
    throw new Error("Interactive driver prompt requires a TTY");
  }

  return await new Promise((resolve, reject) => {
    let input = "";
    let closed = false;
    let countdownTimer = null;
    let lastRenderLength = 0;

    const cleanup = () => {
      if (closed) {
        return;
      }
      closed = true;

      if (countdownTimer) {
        clearInterval(countdownTimer);
      }

      process.stdin.off("keypress", onKeypress);

      if (typeof process.stdin.setRawMode === "function") {
        try {
          process.stdin.setRawMode(false);
        } catch {
          // ignore
        }
      }

      try {
        process.stdin.pause();
      } catch {
        // ignore
      }
    };

    const render = () => {
      const nowMs = Date.now();
      const remainingText = expiresAt ? `Timeout: ${formatCountdownBar(expiresAt, nowMs)} ${formatRemainingTime(expiresAt, nowMs)} left | ` : "";
      const line = `[listen] ${remainingText}${promptText}${input}`;
      const padded = line.padEnd(Math.max(lastRenderLength, line.length), " ");
      lastRenderLength = padded.length;

      readline.clearLine(process.stdout, 0);
      readline.cursorTo(process.stdout, 0);
      process.stdout.write(padded);
    };

    const finish = (answer) => {
      cleanup();
      readline.clearLine(process.stdout, 0);
      readline.cursorTo(process.stdout, 0);
      process.stdout.write("\n");
      resolve(answer);
    };

    const onKeypress = (_str, key = {}) => {
      if (key.ctrl && key.name === "c") {
        cleanup();
        reject(new Error("Interrupted by user"));
        return;
      }

      if (key.name === "return") {
        const answer = input.trim().toLowerCase();
        if (answer === "accept" || answer === "reject") {
          finish(answer);
          return;
        }

        input = "";
        readline.clearLine(process.stdout, 0);
        readline.cursorTo(process.stdout, 0);
        process.stdout.write("[listen] Please type exactly `accept` or `reject`.\n");
        render();
        return;
      }

      if (key.name === "backspace") {
        input = input.slice(0, -1);
        render();
        return;
      }

      if (typeof _str === "string" && _str.length > 0 && !key.meta && !key.ctrl) {
        input += _str;
        render();
      }
    };

    try {
      readline.emitKeypressEvents(process.stdin);
      if (typeof process.stdin.setRawMode === "function") {
        process.stdin.setRawMode(true);
      }
      process.stdin.resume();
      process.stdin.on("keypress", onKeypress);

      if (expiresAt) {
        countdownTimer = setInterval(() => {
          if (formatRemainingTime(expiresAt) === "00:00") {
            render();
            clearInterval(countdownTimer);
            countdownTimer = null;
            return;
          }
          render();
        }, 1000);
      }

      render();
    } catch (error) {
      cleanup();
      reject(error);
    }
  });
}

async function listenForDriverAssignments({
  gatewayUrl,
  realtimeWsUrl,
  runId,
  driverEmail,
  driverAccounts,
  happyPath = false,
  riderAccount = null
}) {
  const driverMap = new Map(driverAccounts.map((driver) => [String(driver.userId), driver]));
  const topics = driverAccounts.map((driver) => `/topic/driver/${driver.userId}`);
  // Realtime gateway requires each driver's own JWT to subscribe to their own
  // /topic/driver/{id} — one WS connection per driver, not one connection watching
  // every candidate's topic, since a single token can't own multiple driver identities.
  const sockets = driverAccounts.map((driver) => ({
    driver,
    topic: `/topic/driver/${driver.userId}`,
    webSocket: null,
    frameBuffer: ""
  }));

  if (driverAccounts.length === 1) {
    console.log("[listen] Driver:", driverEmail);
    console.log("[listen] User ID:", driverAccounts[0].userId);
  } else {
    console.log("[listen] Monitoring drivers:");
    for (const driver of driverAccounts) {
      console.log(`[listen] - ${driver.email} -> user ${driver.userId}`);
    }
  }

  console.log("[listen] Run ID:", runId);
  console.log("[listen] WebSocket:", realtimeWsUrl);
  console.log("[listen] Subscribing to:", topics.join(", "));
  console.log("[listen] Type `accept` or `reject` when an assignment arrives.");
  if (happyPath) {
    console.log("[listen] Happy path enabled: accept will start and complete the ride automatically.");
  }

  let closed = false;
  let promptQueue = Promise.resolve();

  const cleanup = async () => {
    if (closed) {
      return;
    }

    closed = true;
    for (const socket of sockets) {
      try {
        socket.webSocket?.close();
      } catch {
        // ignore
      }
    }
  };

  const exitPromise = new Promise((resolve, reject) => {
    let closedCount = 0;

    sockets.forEach((socket, index) => {
      socket.webSocket = new WebSocket(realtimeWsUrl);
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
              id: `driver-assignment-${index + 1}`,
              destination: topic
            }));
            console.log(`[listen] Connected and subscribed to ${topic}.`);
            continue;
          }

          if (parsed.command === "MESSAGE") {
            promptQueue = promptQueue.then(async () => {
            const payload = safeParseJson(parsed.body);
            console.log("[listen] Assignment received:", JSON.stringify(payload, null, 2));

            const assignedDriver = driverMap.get(String(payload.driverUserId));
            if (!assignedDriver) {
              console.warn(`[listen] No simulated driver account found for driverUserId ${payload.driverUserId}`);
              return;
            }

            const decision = await askForDriverDecision(
              payload.dispatchId,
              payload.driverUserId,
              assignedDriver.email,
              payload.expiresAt
            );
            const accepted = decision === "accept";

            if (accepted) {
              if (happyPath) {
                if (!riderAccount?.accessToken) {
                  throw new Error("Happy path requires a rider account token");
                }

                await handleAcceptedAssignment({
                  gatewayUrl,
                  riderToken: riderAccount.accessToken,
                  rideId: payload.rideId,
                  dispatchId: payload.dispatchId,
                  driverUserId: payload.driverUserId,
                  driverToken: assignedDriver.token,
                  autoStartComplete: true
                });
                await cleanup();
                return;
              }

              await handleAcceptedAssignment({
                gatewayUrl,
                riderToken: riderAccount?.accessToken || assignedDriver.token,
                rideId: payload.rideId,
                dispatchId: payload.dispatchId,
                driverUserId: payload.driverUserId,
                driverToken: assignedDriver.token,
                autoStartComplete: false
              });
            } else {
              await sendDriverResponse({
                gatewayUrl,
                token: assignedDriver.token,
                rideId: payload.rideId,
                dispatchId: payload.dispatchId,
                driverUserId: payload.driverUserId,
                accepted
              });

              console.log(`[listen] Sent REJECT for dispatch ${payload.dispatchId}`);
            }
            }).catch((error) => {
              console.error("[listen] Failed to handle assignment:", error.message);
            });
            continue;
          }

          if (parsed.command === "ERROR") {
            reject(new Error(parsed.body || "WebSocket STOMP error"));
            return;
          }
        }
      });

      webSocket.addEventListener("close", () => {
        closedCount += 1;
        if (!closed && closedCount === sockets.length) {
          resolve();
        }
      });

      webSocket.addEventListener("error", (event) => {
        reject(event.error || new Error("WebSocket connection error"));
      });
    });
  });

  const shutdown = () => {
    void cleanup();
  };

  process.once("SIGINT", shutdown);
  process.once("SIGTERM", shutdown);

  try {
    await exitPromise;
  } finally {
    process.removeListener("SIGINT", shutdown);
    process.removeListener("SIGTERM", shutdown);
    await cleanup();
  }
}

async function runSimulation(args) {
  const plan = buildDriverPlan({
    riderLat: args.pickupLat,
    riderLng: args.pickupLng,
    driverCount: args.driverCount,
    activeDriverCount: args.activeDriverCount,
    radiusMeters: args.radiusMeters,
    seed: args.seed,
    driverPassword: args.driverPassword,
    driverEmailPrefix: args.driverEmailPrefix
  });

  console.log("[sim] Gateway:", args.gatewayUrl);
  console.log("[sim] Run ID:", args.runId);
  console.log("[sim] Rider:", args.riderEmail);
  console.log("[sim] Drivers:", plan.drivers.length, `(${args.activeDriverCount} active, ${args.driverCount - args.activeDriverCount} idle)`);

  let updateTimer;
  let updatesActive = true;
  let shutdownRequested = false;

  const shutdown = () => {
    shutdownRequested = true;
    updatesActive = false;
    if (updateTimer) {
      clearInterval(updateTimer);
    }
  };

  process.once("SIGINT", shutdown);
  process.once("SIGTERM", shutdown);

  try {
    const { rider, driverAccounts, reusedWarmupSession } = await resolveSimulationAccounts(args, plan);
    if (reusedWarmupSession) {
      console.log("[sim] Reusing warmed auth session state.");
    }

    for (const driver of driverAccounts) {
      console.log(`[sim] Driver account ready: ${driver.email} -> user ${driver.userId} [${driver.mode}]`);
    }

    const activeDrivers = driverAccounts.filter((driver) => driver.mode === "active");

    for (const driver of driverAccounts) {
      await goDriverOnline({
        gatewayUrl: args.gatewayUrl,
        token: driver.token,
        driverUserId: driver.userId,
        lat: driver.lat,
        lng: driver.lng
      });
      console.log(`[sim] Driver ${driver.userId} online at (${driver.lat}, ${driver.lng}) [${driver.mode}]`);
    }

    let movementTick = 0;
    updateTimer = setInterval(async () => {
      if (!updatesActive) {
        return;
      }

      movementTick += 1;
      const liveRng = createRng(args.seed + movementTick * 1000);

      for (const driver of activeDrivers) {
        const updatedPoint = nextActivePoint(
          { lat: driver.lat, lng: driver.lng },
          args.pickupLat,
          args.pickupLng,
          40 + liveRng() * 35,
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
          console.log(`[sim] Driver ${driver.userId} moved to (${driver.lat}, ${driver.lng})`);
        } catch (error) {
          console.error(`[sim] Driver ${driver.userId} update failed: ${error.message}`);
        }
      }
    }, args.updateIntervalMs);

    const ridePayload = createRideRequestPayload({
      riderUserId: rider.userId,
      pickup: { label: args.pickupLabel, lat: args.pickupLat, lng: args.pickupLng },
      drop: { label: args.dropLabel, lat: args.dropLat, lng: args.dropLng }
    });

    if (args.startupDelayMs > 0) {
      console.log(`[sim] Waiting ${args.startupDelayMs}ms for listener terminals to subscribe...`);
      await delay(args.startupDelayMs);
    }

    const ride = await createRide({
      gatewayUrl: args.gatewayUrl,
      riderToken: rider.accessToken,
      payload: ridePayload
    });

    console.log("[sim] Ride created:", JSON.stringify(ride, null, 2));
    console.log("[sim] Leave this terminal running while the listener terminal responds.");

    if (!ride.rideId) {
      throw new Error("Ride response did not include rideId");
    }

    void pollDispatch({
      gatewayUrl: args.gatewayUrl,
      riderToken: rider.accessToken,
      rideId: ride.rideId,
      timeoutMs: args.maxWaitMs
    }).then((dispatch) => {
      console.log("[sim] Dispatch assigned:", JSON.stringify(dispatch, null, 2));
    }).catch((error) => {
      console.error(`[sim] Dispatch watch stopped: ${error.message}`);
    });

    await new Promise((resolve) => {
      const checkShutdown = () => {
        if (shutdownRequested) {
          resolve();
          return;
        }
        setTimeout(checkShutdown, 500);
      };

      checkShutdown();
    });
  } finally {
    updatesActive = false;
    if (updateTimer) {
      clearInterval(updateTimer);
    }
    process.removeListener("SIGINT", shutdown);
    process.removeListener("SIGTERM", shutdown);
  }
}

async function warmupSimulation(args) {
  const plan = buildDriverPlan({
    riderLat: args.pickupLat,
    riderLng: args.pickupLng,
    driverCount: args.driverCount,
    activeDriverCount: args.activeDriverCount,
    radiusMeters: args.radiusMeters,
    seed: args.seed,
    driverPassword: args.driverPassword,
    driverEmailPrefix: args.driverEmailPrefix
  });

  console.log("[sim] Gateway:", args.gatewayUrl);
  console.log("[sim] Run ID:", args.runId);
  console.log("[sim] Rider:", args.riderEmail);
  console.log("[sim] Drivers:", plan.drivers.length, `(${args.activeDriverCount} active, ${args.driverCount - args.activeDriverCount} idle)`);
  console.log("[sim] Warmup: bootstrapping auth accounts only...");

  await bootstrapSimulationAccounts(args, plan);

  console.log("[sim] Warmup complete.");
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

async function main() {
  const { mode, args: rawArgs } = normalizeModeAndArgs(process.argv.slice(2));
  const args = parseArgs(rawArgs, mode);

  if (mode === "listen") {
    const sessionData = readSimulationSessionData(args.sessionFile) || {};
    const plan = buildDriverPlan({
      riderLat: args.pickupLat,
      riderLng: args.pickupLng,
      driverCount: args.driverCount,
      activeDriverCount: args.activeDriverCount,
      radiusMeters: args.radiusMeters,
      seed: args.seed,
      driverPassword: args.driverPassword,
      driverEmailPrefix: args.driverEmailPrefix
    });

    const sessionDriverAccounts = buildDriverAccountsFromSession(sessionData, plan);
    const listenPlan = sessionDriverAccounts.length > 0
      ? {
          rider: plan.rider,
          drivers: sessionDriverAccounts
        }
      : plan;

    const allDrivers = await createDriverAccounts({
      gatewayUrl: args.gatewayUrl,
      plan: listenPlan
    });

    const driverAccounts = args.driverEmail
      ? allDrivers.filter((driver) => driver.email === args.driverEmail)
      : allDrivers;

    if (args.driverEmail && driverAccounts.length === 0) {
      throw new Error(`No simulated driver account matched ${args.driverEmail}`);
    }

    const riderAccount = args.happyPath
      ? await ensureAuthUserWithRetry({
          gatewayUrl: args.gatewayUrl,
          email: args.riderEmail,
          password: args.riderPassword,
          roles: ["RIDER"]
        })
      : null;

    await listenForDriverAssignments({
      gatewayUrl: args.gatewayUrl,
      realtimeWsUrl: args.realtimeWsUrl,
      runId: args.runId,
      driverEmail: resolveDriverEmail(args),
      driverAccounts,
      happyPath: args.happyPath,
      riderAccount
    });
    return;
  }

  if (mode === "warmup") {
    await warmupSimulation(args);
    return;
  }

  await runSimulation(args);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch((error) => {
    console.error("[sim] Simulation failed:", error.message);
    process.exitCode = 1;
  });
}
