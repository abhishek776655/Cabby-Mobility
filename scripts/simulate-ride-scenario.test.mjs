import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import {
  buildDefaultSimulationEmails,
  buildDriverPlan,
  buildStompFrame,
  createRideRequestPayload,
  ensureAuthUserWithRetry,
  formatCountdownBar,
  formatRemainingTime,
  handleAcceptedAssignment,
  loadSimulationAccountsFromSession,
  parseStompFrame
} from "./simulate-ride-scenario.mjs";

test("buildDriverPlan splits active and idle drivers deterministically", () => {
  const plan = buildDriverPlan({
    riderLat: 28.6139,
    riderLng: 77.209,
    driverCount: 4,
    activeDriverCount: 2,
    radiusMeters: 800,
    seed: 123
  });

  assert.equal(plan.drivers.length, 4);
  assert.equal(plan.drivers.filter((driver) => driver.mode === "active").length, 2);
  assert.equal(plan.drivers.filter((driver) => driver.mode === "idle").length, 2);
  assert.ok(plan.drivers.every((driver) => typeof driver.lat === "number" && typeof driver.lng === "number"));
});

test("createRideRequestPayload maps rider and trip coordinates", () => {
  const payload = createRideRequestPayload({
    riderUserId: 101,
    pickup: { label: "Connaught Place", lat: 28.6139, lng: 77.209 },
    drop: { label: "India Gate", lat: 28.6129, lng: 77.2295 }
  });

  assert.deepEqual(payload, {
    riderUserId: 101,
    pickupLocation: "Connaught Place",
    dropLocation: "India Gate",
    pickupLatitude: 28.6139,
    pickupLongitude: 77.209,
    dropLatitude: 28.6129,
    dropLongitude: 77.2295
  });
});

test("buildDefaultSimulationEmails adds a run-specific suffix", () => {
  const emails = buildDefaultSimulationEmails("run123");

  assert.equal(emails.riderEmail, "sim.rider.run123@example.com");
  assert.equal(emails.driverEmailPrefix, "sim.driver.run123.");
});

test("buildStompFrame and parseStompFrame round-trip a message", () => {
  const frame = buildStompFrame("MESSAGE", {
    destination: "/topic/driver/1",
    subscription: "driver-assignment-1"
  }, JSON.stringify({
    eventType: "ASSIGNMENT_REQUESTED",
    dispatchId: "dispatch-1"
  }));

  const parsed = parseStompFrame(frame);

  assert.equal(parsed.command, "MESSAGE");
  assert.equal(parsed.headers.destination, "/topic/driver/1");
  assert.equal(parsed.headers.subscription, "driver-assignment-1");
  assert.deepEqual(JSON.parse(parsed.body), {
    eventType: "ASSIGNMENT_REQUESTED",
    dispatchId: "dispatch-1"
  });
});

test("formatRemainingTime renders mm:ss from a deadline", () => {
  assert.equal(formatRemainingTime(Date.now() + 30_000, Date.now()), "00:30");
  assert.equal(formatRemainingTime(Date.now() - 1_000, Date.now()), "00:00");
});

test("formatCountdownBar renders a filled bar from remaining time", () => {
  assert.equal(formatCountdownBar(Date.now() + 30_000, Date.now(), 10, 30), "[██████████]");
  assert.equal(formatCountdownBar(Date.now(), Date.now(), 10, 30), "[░░░░░░░░░░]");
});

test("handleAcceptedAssignment starts and completes the ride", async () => {
  const calls = [];
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (url, options = {}) => {
    calls.push({
      url: String(url),
      method: options.method || "GET"
    });

    const path = String(url).replace("http://gateway", "");

    if (path === "/dispatch/driver-response") {
      return new Response(JSON.stringify({
        success: true,
        data: null
      }), { status: 200 });
    }

    if (path === "/rides/ride-1") {
      const status = calls.filter((call) => call.url.endsWith("/rides/ride-1")).length <= 1
        ? "MATCHING"
        : calls.filter((call) => call.url.endsWith("/rides/ride-1")).length === 2
          ? "DRIVER_ASSIGNED"
          : "ONGOING";

      return new Response(JSON.stringify({
        success: true,
        data: {
          rideId: "ride-1",
          status
        }
      }), { status: 200 });
    }

    if (path === "/rides/ride-1/start") {
      return new Response(JSON.stringify({
        success: true,
        data: {
          rideId: "ride-1",
          status: "ONGOING"
        }
      }), { status: 200 });
    }

    if (path === "/rides/ride-1/complete") {
      return new Response(JSON.stringify({
        success: true,
        data: {
          rideId: "ride-1",
          status: "COMPLETED"
        }
      }), { status: 200 });
    }

    throw new Error(`Unexpected request: ${options.method || "GET"} ${path}`);
  };

  try {
    await handleAcceptedAssignment({
      gatewayUrl: "http://gateway",
      riderToken: "rider-token",
      rideId: "ride-1",
      dispatchId: "dispatch-1",
      driverUserId: 7,
      driverToken: "driver-token",
      autoStartComplete: true,
      statusTimeoutMs: 250,
      statusPollMs: 0,
      completionDelayMs: 0
    });
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.deepEqual(
    calls.map((call) => `${call.method} ${call.url}`),
    [
      "POST http://gateway/dispatch/driver-response",
      "GET http://gateway/rides/ride-1",
      "GET http://gateway/rides/ride-1",
      "POST http://gateway/rides/ride-1/start",
      "GET http://gateway/rides/ride-1",
      "POST http://gateway/rides/ride-1/complete"
    ]
  );
});

test("handleAcceptedAssignment stops before completion if the ride never becomes ongoing", async () => {
  const calls = [];
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (url, options = {}) => {
    calls.push({
      url: String(url),
      method: options.method || "GET"
    });

    const path = String(url).replace("http://gateway", "");

    if (path === "/dispatch/driver-response") {
      return new Response(JSON.stringify({
        success: true,
        data: null
      }), { status: 200 });
    }

    if (path === "/rides/ride-1") {
      return new Response(JSON.stringify({
        success: true,
        data: {
          rideId: "ride-1",
          status: calls.some((call) => call.url.endsWith("/rides/ride-1/start"))
            ? "STARTED"
            : "DRIVER_ASSIGNED"
        }
      }), { status: 200 });
    }

    if (path === "/rides/ride-1/start") {
      return new Response(JSON.stringify({
        success: true,
        data: {
          rideId: "ride-1",
          status: "STARTED"
        }
      }), { status: 200 });
    }

    if (path === "/rides/ride-1/complete") {
      throw new Error("complete should not be called when the ride never becomes ongoing");
    }

    throw new Error(`Unexpected request: ${options.method || "GET"} ${path}`);
  };

  try {
    await assert.rejects(
      () => handleAcceptedAssignment({
        gatewayUrl: "http://gateway",
        riderToken: "rider-token",
        rideId: "ride-1",
        dispatchId: "dispatch-1",
        driverUserId: 7,
        driverToken: "driver-token",
        autoStartComplete: true,
        statusTimeoutMs: 5,
        statusPollMs: 0,
        completionDelayMs: 0
      }),
      /Timed out waiting for ride ride-1 to reach ONGOING/
    );
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.ok(calls.some((call) => call.url.endsWith("/rides/ride-1/start")));
  assert.ok(!calls.some((call) => call.url.endsWith("/rides/ride-1/complete")));
});

test("ensureAuthUserWithRetry retries until auth bootstrap succeeds", async () => {
  const calls = [];
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (url, options = {}) => {
    calls.push(`${options.method || "GET"} ${String(url)}`);

    if (calls.length < 3) {
      return new Response("temporary failure", { status: 503 });
    }

    return new Response(JSON.stringify({
      success: true,
      data: {
        userId: 123,
        accessToken: "token-123",
        roles: ["RIDER"]
      }
    }), { status: 200 });
  };

  try {
    const result = await ensureAuthUserWithRetry({
      gatewayUrl: "http://gateway",
      email: "sim.rider@example.com",
      password: "Passw0rd!123",
      roles: ["RIDER"],
      retries: 3,
      delayMs: 0
    });

    assert.equal(result.userId, 123);
    assert.equal(result.accessToken, "token-123");
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(calls.length, 3);
  assert.deepEqual(calls, [
    "POST http://gateway/auth/login",
    "POST http://gateway/auth/register",
    "POST http://gateway/auth/login"
  ]);
});

test("loadSimulationAccountsFromSession reuses warmed auth state when available", () => {
  const sessionDir = fs.mkdtempSync(path.join(os.tmpdir(), "smart-mobility-session-"));
  const sessionFile = path.join(sessionDir, "session.json");

  fs.writeFileSync(sessionFile, JSON.stringify({
    riderUserId: 101,
    riderAccessToken: "rider-token",
    riderRoles: ["RIDER"],
    driverAccounts: [
      { userId: 201, accessToken: "driver-token-1" },
      { userId: 202, accessToken: "driver-token-2" }
    ]
  }));

  const plan = buildDriverPlan({
    riderLat: 28.6139,
    riderLng: 77.209,
    driverCount: 2,
    activeDriverCount: 1,
    radiusMeters: 700,
    seed: 100
  });

  const accounts = loadSimulationAccountsFromSession({
    sessionFile
  }, plan);

  assert.ok(accounts);
  assert.equal(accounts.rider.userId, 101);
  assert.equal(accounts.rider.accessToken, "rider-token");
  assert.equal(accounts.driverAccounts[0].userId, 201);
  assert.equal(accounts.driverAccounts[0].token, "driver-token-1");
  assert.equal(accounts.driverAccounts[1].userId, 202);
  assert.equal(accounts.driverAccounts[1].token, "driver-token-2");
});
