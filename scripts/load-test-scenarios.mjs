import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";
import fs from "node:fs";
import { setTimeout as delay } from "node:timers/promises";

const MODULE_DIR = path.dirname(fileURLToPath(import.meta.url));
const SIMULATOR_SCRIPT = path.join(MODULE_DIR, "simulate-ride-scenario.mjs");
const DEFAULT_SESSION_DIR = path.join(process.cwd(), ".tmp", "smart-mobility-pressure");

function assertPositiveInteger(value, name) {
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
}

function asInteger(value, fallback) {
  return Number.isFinite(Number(value)) ? Number(value) : fallback;
}

async function waitForHttpOk(url, {
  timeoutMs = 180_000,
  intervalMs = 1_000,
  fetchImpl = globalThis.fetch
} = {}) {
  const deadline = Date.now() + timeoutMs;
  let lastError = null;

  while (Date.now() < deadline) {
    try {
      const response = await fetchImpl(url);
      if (response.ok) {
        return;
      }
      lastError = new Error(`${response.status} ${response.statusText || "Request failed"}`);
    } catch (error) {
      lastError = error;
    }

    if (intervalMs > 0) {
      await delay(intervalMs);
    }
  }

  throw new Error(`Timed out waiting for ${url} to become healthy${lastError ? `: ${lastError.message}` : ""}`);
}

export async function waitForAuthReadiness({
  gatewayUrl = "http://localhost:8080",
  timeoutMs = 180_000,
  intervalMs = 1_000,
  fetchImpl = globalThis.fetch
} = {}) {
  const url = `${gatewayUrl.replace(/\/$/, "")}/auth/login`;
  const deadline = Date.now() + timeoutMs;
  let lastError = null;

  while (Date.now() < deadline) {
    try {
      const response = await fetchImpl(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          email: "readiness.probe@example.com",
          password: "invalid-password"
        })
      });

      if (response.status >= 200 && response.status < 500) {
        if (response.status >= 500) {
          lastError = new Error(`${response.status} ${response.statusText || "Request failed"}`);
        } else {
          return;
        }
      } else {
        lastError = new Error(`${response.status} ${response.statusText || "Request failed"}`);
      }
    } catch (error) {
      lastError = error;
    }

    if (intervalMs > 0) {
      await delay(intervalMs);
    }
  }

  throw new Error(`Timed out waiting for auth readiness${lastError ? `: ${lastError.message}` : ""}`);
}

export async function waitForServiceReadiness({
  gatewayUrl = "http://localhost:8080",
  healthUrls,
  timeoutMs = 180_000,
  intervalMs = 1_000,
  fetchImpl = globalThis.fetch
} = {}) {
  const urls = Array.isArray(healthUrls) && healthUrls.length > 0
    ? healthUrls
    : [`${gatewayUrl.replace(/\/$/, "")}/actuator/health`];

  for (const url of urls) {
    await waitForHttpOk(url, {
      timeoutMs,
      intervalMs,
      fetchImpl
    });
  }
}

export function buildScenarioLaunches({
  gatewayUrl,
  scenarioCount,
  driverCount,
  activeDriverCount,
  radiusMeters,
  seed,
  updateIntervalMs,
  startupDelayMs,
  maxWaitMs,
  sessionDir = DEFAULT_SESSION_DIR,
  runPrefix = "pressure",
  simulatorMode = "run",
  nodeBin = process.execPath,
  simulatorScript = SIMULATOR_SCRIPT
}) {
  assertPositiveInteger(scenarioCount, "scenarioCount");

  const launches = [];
  for (let index = 0; index < scenarioCount; index += 1) {
    const runId = `${runPrefix}-${index + 1}`;
    const riderEmail = `sim.rider.${runId}@example.com`;
    const driverEmailPrefix = `sim.driver.${runId}.`;
    const sessionFile = path.join(sessionDir, `${runId}.json`);

    launches.push({
      command: nodeBin,
      runId,
      index: index + 1,
      riderEmail,
      driverEmailPrefix,
      driverCount,
      activeDriverCount,
      sessionFile,
      args: [
        simulatorScript,
        simulatorMode,
        "--gateway-url",
        gatewayUrl,
        "--run-id",
        runId,
        "--rider-email",
        riderEmail,
        "--driver-email-prefix",
        driverEmailPrefix,
        "--driver-count",
        String(driverCount),
        "--active-driver-count",
        String(activeDriverCount),
        "--radius-meters",
        String(radiusMeters),
        "--seed",
        String(seed + index),
        "--update-interval-ms",
        String(updateIntervalMs),
        "--startup-delay-ms",
        String(startupDelayMs),
        "--max-wait-ms",
        String(maxWaitMs)
      ],
      env: {
        ...process.env,
        SIMULATOR_SESSION_FILE: sessionFile,
        SIMULATOR_RUN_ID: runId
      }
    });
  }

  return launches;
}

function spawnScenario(launch) {
  return spawn(launch.command, launch.args, {
    env: launch.env,
    stdio: ["ignore", "pipe", "pipe"]
  });
}

function waitForChildExit(child) {
  return new Promise((resolve) => {
    child.once("exit", (code, signal) => {
      resolve({ code, signal });
    });
  });
}

function attachChildLogging(child, launch) {
  const logDir = path.join(launch.sessionFile ? path.dirname(launch.sessionFile) : DEFAULT_SESSION_DIR, "logs");
  fs.mkdirSync(logDir, { recursive: true });
  const logFile = path.join(logDir, `${launch.runId}.log`);
  const stream = fs.createWriteStream(logFile, { flags: "a" });
  const prefix = `[${launch.runId}] `;
  let stdoutBuffer = "";
  let stderrBuffer = "";

  const writeChunk = (prefix, chunk) => {
    const text = Buffer.isBuffer(chunk) ? chunk.toString("utf8") : String(chunk);
    stream.write(`${prefix}${text}`);
  };

  const mirrorLineStream = (targetWrite, buffer, chunk) => {
    const text = Buffer.isBuffer(chunk) ? chunk.toString("utf8") : String(chunk);
    const combined = buffer + text;
    const lines = combined.split(/\r?\n/);
    const nextBuffer = lines.pop() ?? "";
    for (const line of lines) {
      if (line.length > 0) {
        targetWrite(`${prefix}${line}\n`);
      } else {
        targetWrite("\n");
      }
    }
    return nextBuffer;
  };

  child.stdout.on("data", (chunk) => {
    writeChunk("", chunk);
    stdoutBuffer = mirrorLineStream((line) => process.stdout.write(line), stdoutBuffer, chunk);
  });
  child.stderr.on("data", (chunk) => {
    writeChunk("[stderr] ", chunk);
    stderrBuffer = mirrorLineStream((line) => process.stderr.write(line), stderrBuffer, chunk);
  });
  child.once("exit", () => {
    stream.end();
  });

  return logFile;
}

function tailFile(filePath, maxLines = 40) {
  try {
    const content = fs.readFileSync(filePath, "utf8").trimEnd();
    if (!content) {
      return "";
    }

    const lines = content.split(/\r?\n/);
    return lines.slice(-maxLines).join("\n");
  } catch {
    return "";
  }
}

async function runLaunchesSequentially(launches, label, interLaunchDelayMs = 0) {
  const results = [];

  for (const [index, launch] of launches.entries()) {
    console.log(`[pressure] ${label} ${index + 1}/${launches.length}: starting ${launch.runId} (rider ${launch.riderEmail}, drivers ${launch.driverCount})`);
    const child = spawnScenario(launch);
    const logFile = attachChildLogging(child, launch);
    const { code, signal } = await waitForChildExit(child);

    results.push({
      launch,
      code,
      signal,
      logFile
    });

    if (code !== 0 && code !== null) {
      const tail = logFile ? tailFile(logFile) : "";
      const tailText = tail
        ? `\n--- ${launch.runId} tail (${logFile}) ---\n${tail}`
        : `\n--- ${launch.runId} tail unavailable (${logFile || "no log file"}) ---`;
      throw new Error(`${label} failed: ${launch.runId} exited with code ${code}${signal ? ` signal ${signal}` : ""}${tailText}`);
    }

    console.log(`[pressure] ${label} ${index + 1}/${launches.length}: completed ${launch.runId}`);

    if (interLaunchDelayMs > 0 && index < launches.length - 1) {
      await delay(interLaunchDelayMs);
    }
  }

  return results;
}

export async function runLoadTest(options) {
  const scenarioCount = asInteger(options.scenarioCount, 0);
  const durationMs = asInteger(options.durationMs, 60_000);
  const launchStaggerMs = asInteger(options.launchStaggerMs, 1_000);
  const warmupStaggerMs = asInteger(options.warmupStaggerMs, 500);

  await waitForServiceReadiness({
    gatewayUrl: options.gatewayUrl,
    timeoutMs: asInteger(options.readinessTimeoutMs, 180_000),
    intervalMs: asInteger(options.readinessPollMs, 1_000)
  });

  await waitForAuthReadiness({
    gatewayUrl: options.gatewayUrl,
    timeoutMs: asInteger(options.readinessTimeoutMs, 180_000),
    intervalMs: asInteger(options.readinessPollMs, 1_000)
  });

  const warmupLaunches = buildScenarioLaunches({
    ...options,
    scenarioCount,
    simulatorMode: "warmup"
  });

  console.log(`[pressure] Warming up ${warmupLaunches.length} simulator scenario(s)...`);
  await runLaunchesSequentially(warmupLaunches, "Warmup phase", warmupStaggerMs);
  console.log("[pressure] Warmup complete. Starting parallel traffic phase...");

  const launches = buildScenarioLaunches({
    ...options,
    scenarioCount
  });

  console.log(`[pressure] Starting ${launches.length} simulator scenario(s)...`);

  const children = [];
  for (const [index, launch] of launches.entries()) {
    console.log(`[pressure] Launching ${launch.runId} (${index + 1}/${launches.length}) rider=${launch.riderEmail} drivers=${launch.driverCount} active=${launch.activeDriverCount}`);
    const childEntry = {
      launch,
      child: spawnScenario(launch),
      exited: false,
      code: null,
      signal: null,
      logFile: null
    };

    childEntry.logFile = attachChildLogging(childEntry.child, launch);
    childEntry.child.once("exit", (code, signal) => {
      childEntry.exited = true;
      childEntry.code = code;
      childEntry.signal = signal;
    });

    children.push(childEntry);

    if (launchStaggerMs > 0 && index < launches.length - 1) {
      await delay(launchStaggerMs);
    }
  }

  const heartbeat = setInterval(() => {
    const exited = children.filter((entry) => entry.exited).length;
    const active = children.length - exited;
    console.log(`[pressure] Progress: ${active} active, ${exited} exited, ${children.length} total`);
  }, 10_000);

  await delay(durationMs);
  console.log(`[pressure] Stopping scenarios after ${durationMs}ms...`);
  clearInterval(heartbeat);

  for (const entry of children) {
    if (!entry.exited) {
      entry.child.kill("SIGINT");
    }
  }

  const exitResults = await Promise.all(children.map(async (entry) => {
    if (entry.exited) {
      return entry;
    }

    await new Promise((resolve) => {
      entry.child.once("exit", () => resolve());
    });

    return entry;
  }));

  const failures = exitResults.filter((entry) => entry.code !== 0 && entry.code !== null);
  if (failures.length > 0) {
    const summary = failures
      .map((entry) => `${entry.launch.runId} exited with code ${entry.code}${entry.signal ? ` signal ${entry.signal}` : ""}`)
      .join(", ");
    const details = failures
      .map((entry) => {
        const tail = entry.logFile ? tailFile(entry.logFile) : "";
        return tail
          ? `\n--- ${entry.launch.runId} tail (${entry.logFile}) ---\n${tail}`
          : `\n--- ${entry.launch.runId} tail unavailable (${entry.logFile || "no log file"}) ---`;
      })
      .join("");
    throw new Error(`Pressure test failed: ${summary}${details}`);
  }

  console.log("[pressure] Load test finished cleanly.");
}

function parseArgs(argv) {
  const args = {
    gatewayUrl: "http://localhost:8080",
    scenarioCount: 4,
    driverCount: 4,
    activeDriverCount: 2,
    radiusMeters: 700,
    seed: 100,
    updateIntervalMs: 2000,
    startupDelayMs: 0,
    maxWaitMs: 15_000,
    durationMs: 60_000,
    launchStaggerMs: 1_000,
    warmupStaggerMs: 500,
    readinessTimeoutMs: 180_000,
    readinessPollMs: 1_000,
    sessionDir: DEFAULT_SESSION_DIR,
    runPrefix: "pressure"
  };

  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const value = argv[index + 1];

    switch (flag) {
      case "--gateway-url":
        args.gatewayUrl = value;
        index += 1;
        break;
      case "--scenarios":
        args.scenarioCount = asInteger(value, args.scenarioCount);
        index += 1;
        break;
      case "--driver-count":
        args.driverCount = asInteger(value, args.driverCount);
        index += 1;
        break;
      case "--active-driver-count":
        args.activeDriverCount = asInteger(value, args.activeDriverCount);
        index += 1;
        break;
      case "--radius-meters":
        args.radiusMeters = asInteger(value, args.radiusMeters);
        index += 1;
        break;
      case "--seed":
        args.seed = asInteger(value, args.seed);
        index += 1;
        break;
      case "--update-interval-ms":
        args.updateIntervalMs = asInteger(value, args.updateIntervalMs);
        index += 1;
        break;
      case "--startup-delay-ms":
        args.startupDelayMs = asInteger(value, args.startupDelayMs);
        index += 1;
        break;
      case "--max-wait-ms":
        args.maxWaitMs = asInteger(value, args.maxWaitMs);
        index += 1;
        break;
      case "--duration-ms":
        args.durationMs = asInteger(value, args.durationMs);
        index += 1;
        break;
      case "--launch-stagger-ms":
        args.launchStaggerMs = asInteger(value, args.launchStaggerMs);
        index += 1;
        break;
      case "--warmup-stagger-ms":
        args.warmupStaggerMs = asInteger(value, args.warmupStaggerMs);
        index += 1;
        break;
      case "--readiness-timeout-ms":
        args.readinessTimeoutMs = asInteger(value, args.readinessTimeoutMs);
        index += 1;
        break;
      case "--readiness-poll-ms":
        args.readinessPollMs = asInteger(value, args.readinessPollMs);
        index += 1;
        break;
      case "--session-dir":
        args.sessionDir = value;
        index += 1;
        break;
      case "--run-prefix":
        args.runPrefix = value;
        index += 1;
        break;
      default:
        throw new Error(`Unknown flag: ${flag}`);
    }
  }

  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  await runLoadTest(args);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch((error) => {
    console.error("[pressure] Pressure test failed:", error.message);
    process.exitCode = 1;
  });
}
