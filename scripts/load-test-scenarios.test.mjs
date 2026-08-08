import test from "node:test";
import assert from "node:assert/strict";

import { buildScenarioLaunches, waitForAuthReadiness, waitForServiceReadiness } from "./load-test-scenarios.mjs";

test("buildScenarioLaunches creates unique simulator commands", () => {
  const launches = buildScenarioLaunches({
    gatewayUrl: "http://localhost:8080",
    scenarioCount: 3,
    driverCount: 4,
    activeDriverCount: 2,
    radiusMeters: 700,
    seed: 100,
    updateIntervalMs: 2000,
    startupDelayMs: 0,
    maxWaitMs: 15000,
    sessionDir: "/tmp/smart-mobility-pressure"
  });

  assert.equal(launches.length, 3);
  assert.equal(new Set(launches.map((launch) => launch.args.join(" "))).size, 3);
  assert.ok(launches.every((launch) => launch.args.includes("run")));
  assert.ok(launches.every((launch) => launch.env.SIMULATOR_SESSION_FILE.includes("/tmp/smart-mobility-pressure")));
  assert.deepEqual(launches.map((launch) => launch.args[launch.args.indexOf("--run-id") + 1]), [
    "pressure-1",
    "pressure-2",
    "pressure-3"
  ]);
});

test("buildScenarioLaunches rejects invalid scenario counts", () => {
  assert.throws(() => {
    buildScenarioLaunches({
      gatewayUrl: "http://localhost:8080",
      scenarioCount: 0,
      driverCount: 4,
      activeDriverCount: 2,
      radiusMeters: 700,
      seed: 100,
      updateIntervalMs: 2000,
      startupDelayMs: 0,
      maxWaitMs: 15000,
      sessionDir: "/tmp/smart-mobility-pressure"
    });
  }, /scenarioCount must be a positive integer/);
});

test("buildScenarioLaunches supports custom simulator modes", () => {
  const launches = buildScenarioLaunches({
    gatewayUrl: "http://localhost:8080",
    scenarioCount: 1,
    driverCount: 4,
    activeDriverCount: 2,
    radiusMeters: 700,
    seed: 100,
    updateIntervalMs: 2000,
    startupDelayMs: 0,
    maxWaitMs: 15000,
    sessionDir: "/tmp/smart-mobility-pressure",
    simulatorMode: "warmup"
  });

  assert.equal(launches[0].args[1], "warmup");
});

test("waitForServiceReadiness waits until every service becomes healthy", async () => {
  const calls = [];
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (url) => {
    calls.push(String(url));
    if (calls.length < 3) {
      return new Response("not ready", { status: 503 });
    }

    return new Response(JSON.stringify({ status: "UP" }), { status: 200 });
  };

  try {
    await waitForServiceReadiness({
      healthUrls: ["http://localhost:8080/actuator/health"],
      timeoutMs: 500,
      intervalMs: 0
    });
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(calls.length, 3);
});

test("waitForAuthReadiness waits for auth to return a client error instead of 5xx", async () => {
  const calls = [];
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (url, options = {}) => {
    calls.push(`${options.method || "GET"} ${String(url)}`);
    if (calls.length < 2) {
      return new Response("temporarily unavailable", { status: 503 });
    }

    return new Response(JSON.stringify({ success: false }), { status: 401 });
  };

  try {
    await waitForAuthReadiness({
      gatewayUrl: "http://localhost:8080",
      timeoutMs: 500,
      intervalMs: 0
    });
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(calls.length, 2);
  assert.deepEqual(calls, [
    "POST http://localhost:8080/auth/login",
    "POST http://localhost:8080/auth/login"
  ]);
});
