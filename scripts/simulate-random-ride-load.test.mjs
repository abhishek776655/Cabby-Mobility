import test from "node:test";
import assert from "node:assert/strict";

import {
  createSimulationStats,
  buildRiderProfiles,
  buildSharedDriverPlan,
  formatDashboardScreen,
  formatLoadSummary,
  parseArgs,
  runRideLoop,
  shouldAcceptAssignment
} from "./simulate-random-ride-load.mjs";

test("shouldAcceptAssignment respects the probability threshold", () => {
  const alwaysLow = () => 0.25;
  const alwaysHigh = () => 0.9;

  assert.equal(shouldAcceptAssignment(alwaysLow, 0.7), true);
  assert.equal(shouldAcceptAssignment(alwaysHigh, 0.7), false);
});

test("formatLoadSummary prints the live active counts", () => {
  const stats = createSimulationStats(4);
  stats.activeRides = 1;
  stats.completedRides = 3;
  stats.failedRides = 1;
  stats.acceptedAssignments = 2;
  stats.rejectedAssignments = 5;

  assert.equal(
    formatLoadSummary(stats),
    "[load] activeRides=1 activeRiders=1 activeDrivers=4 completedRides=3 failedRides=1 acceptedAssignments=2 rejectedAssignments=5"
  );
});

test("parseArgs defaults to one rider and accepts multi-rider mode", () => {
  const single = parseArgs([]);
  const multi = parseArgs(["--riders", "4"]);
  const dashboard = parseArgs(["--dashboard", "--dashboard-refresh-ms", "750"]);

  assert.equal(single.riders, 1);
  assert.equal(multi.riders, 4);
  assert.equal(dashboard.dashboard, true);
  assert.equal(dashboard.dashboardRefreshMs, 750);
});

test("formatDashboardScreen prints the live metrics frame", () => {
  const stats = createSimulationStats(6, 4);
  stats.activeRides = 3;
  stats.ridesCreated = 9;
  stats.completedRides = 5;
  stats.failedRides = 2;
  stats.acceptedAssignments = 11;
  stats.rejectedAssignments = 7;
  stats.lastEvent = "[load] Rider sim.rider.demo-load.1@example.com cycle 2: created ride 123";

  const screen = formatDashboardScreen({
    args: {
      runId: "demo-load",
      gatewayUrl: "http://localhost:8080",
      realtimeWsUrl: "ws://localhost:8095/ws"
    },
    stats,
    startedAt: Date.now() - 12_000,
    now: Date.now()
  });

  assert.match(screen, /SMART MOBILITY RANDOM LOAD DASHBOARD/);
  assert.match(screen, /Active rides:\s+3/);
  assert.match(screen, /Active riders:\s+4/);
  assert.match(screen, /Active drivers:\s+6/);
  assert.match(screen, /Accepted:\s+11/);
  assert.match(screen, /Rejected:\s+7/);
  assert.match(screen, /Last event:/);
});

test("buildRiderProfiles creates unique rider accounts and locations", () => {
  const riders = buildRiderProfiles({
    runId: "demo",
    riderCount: 3,
    pickupLabel: "Pickup",
    pickupLat: 28.6139,
    pickupLng: 77.209,
    dropLabel: "Drop",
    dropLat: 28.6129,
    dropLng: 77.2295,
    radiusMeters: 700,
    seed: 123
  });

  assert.equal(riders.length, 3);
  assert.equal(new Set(riders.map((rider) => rider.email)).size, 3);
  assert.ok(riders.every((rider) => rider.pickup && rider.drop));
});

test("buildSharedDriverPlan reuses the same driver pool across riders", () => {
  const riders = buildRiderProfiles({
    runId: "demo",
    riderCount: 2,
    pickupLabel: "Pickup",
    pickupLat: 28.6139,
    pickupLng: 77.209,
    dropLabel: "Drop",
    dropLat: 28.6129,
    dropLng: 77.2295,
    radiusMeters: 700,
    seed: 123
  });

  const plan = buildSharedDriverPlan({
    riderProfiles: riders,
    driverCount: 6,
    activeDriverCount: 3,
    radiusMeters: 700,
    seed: 123
  });

  assert.equal(plan.drivers.length, 6);
  assert.equal(new Set(plan.drivers.map((driver) => driver.email)).size, 6);
  assert.equal(plan.drivers.filter((driver) => driver.mode === "active").length, 3);
});

test("runRideLoop starts a fresh cycle after each completed ride", async () => {
  const calls = [];

  await runRideLoop({
    maxCycles: 3,
    rideCooldownMs: 0,
    createRideCycle: async ({ cycle }) => {
      calls.push(`ride-${cycle}`);
    }
  });

  assert.deepEqual(calls, ["ride-1", "ride-2", "ride-3"]);
});
