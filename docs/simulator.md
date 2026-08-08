# Smart Mobility Simulator

This repo includes a repeatable single-rider / multi-driver simulator for ride request, driver assignment, and interactive driver response flows.

It now covers the full dispatch loop we fixed during debugging:
- ride creation
- nearby driver discovery
- sequential assignment requests
- driver accept or reject
- timeout retry to the next driver
- final `NO_DRIVER_AVAILABLE` failure when all candidates are exhausted

## Option 1: Two-Terminal CLI Flow

Terminal 1 starts the rider and driver simulation:

Run against the local gateway:

```bash
node scripts/simulate-ride-scenario.mjs run \
  --gateway-url http://localhost:8080 \
  --run-id demo1 \
  --driver-count 6 \
  --active-driver-count 3 \
  --radius-meters 900
```

What it does:
- Registers or logs in one rider and multiple drivers through `/auth`
- Marks every driver online through `/location/driver/online`
- Periodically updates the active drivers through `/location/driver/update`
- Waits a few seconds before creating the ride so a listener terminal can subscribe
- Creates one ride through `/rides`
- Keeps the scenario alive until you stop it with `Ctrl+C`

Terminal 2 subscribes as the driver set for that run and responds interactively:

```bash
node scripts/simulate-ride-scenario.mjs listen \
  --gateway-url http://localhost:8080 \
  --realtime-ws-url ws://localhost:8095/ws \
  --run-id demo1 \
  --driver-index 1
```

What it does:
- Logs in or registers the simulated driver accounts for the same `--run-id`
- Subscribes to `/topic/driver/{driverUserId}` for each simulated driver over STOMP at `/ws`
- Prompts `accept` or `reject` in the terminal when any assignment arrives
- Shows a live inline progress bar and countdown on the same terminal line using the assignment event's `expiresAt`
- Sends the chosen response through `/dispatch/driver-response` with the correct driver token

If you want the full happy path, add `--happy-path` to the listener command. In that mode:
- after `accept`, the simulator waits for the ride to become `DRIVER_ASSIGNED`
- it then calls `POST /rides/{rideId}/start` with the driver token
- once the ride is `ONGOING`, it waits 5 seconds
- it then calls `POST /rides/{rideId}/complete`

Behavior notes:
- The response timeout is a single backend-configured window shared by dispatch expiry and Redis reservation TTL
- `accept` finalizes the current assignment and should not trigger another prompt for the same dispatch
- with `--happy-path`, `accept` also starts and completes the ride automatically
- `reject` releases the current driver and retries the next eligible driver
- If all drivers reject or time out, matchmaking publishes `matchmaking-failed` and cab-service moves the ride to `NO_DRIVER_AVAILABLE`
- The listener keeps the prompt on one line while the timer updates, so you can type without the console spamming extra countdown lines

If you omit `--run-id`, both terminals now reuse the latest simulator session automatically. You can still pass an explicit `--run-id` if you want to pin a specific scenario.

Useful overrides:

```bash
GATEWAY_URL=http://localhost:8080 \
RIDER_EMAIL=sim.rider@example.com \
DRIVER_COUNT=8 \
ACTIVE_DRIVER_COUNT=4 \
SIMULATOR_SEED=123 \
node scripts/simulate-ride-scenario.mjs run
```

To listen as a specific driver account instead of the whole simulated driver set, pass `--driver-email`. Keep the same `--run-id` in both terminals if you want the generated rider and driver emails to match:

```bash
node scripts/simulate-ride-scenario.mjs listen \
  --gateway-url http://localhost:8080 \
  --realtime-ws-url ws://localhost:8095/ws \
  --run-id demo1 \
  --driver-email sim.driver.demo1.1@example.com
```

## Option 2: Postman

The root collection already includes a reusable simulator section in `smart-mobility-platform.postman_collection.json`:
- `07. Real-Time Simulator`
- `Simulate 10 Drivers Polling`
- `Stop 10-Driver Simulator`

That workflow is useful if you want to manually inspect requests and responses while the scenario is running.

## Troubleshooting

- If the ride stays in `MATCHING` after all drivers reject, make sure `cab-service` is running with the current `matchmaking-failed` consumer code and that `matchmaking-service` is publishing the failure event.
- If the listener prompt appears on multiple lines, restart the terminal session so it picks up the latest inline progress-bar renderer.
- If acceptance appears to trigger another prompt, verify you responded before the countdown expired and that both simulator terminals are using the same `--run-id`.

## Pressure Test

To stress the stack with a small number of simulated users, first start the low-resource Compose overlay:

```bash
docker compose -f ../docker/docker-compose.yml -f ../docker/docker-compose.pressure.yml up -d
```

Then run several simulator scenarios in parallel:

```bash
node scripts/load-test-scenarios.mjs --scenarios 4 --duration-ms 60000
```

The runner now does this in two phases:
- sequential auth warmup for each scenario
- parallel ride traffic for the actual pressure phase, reusing the warmed auth session instead of re-registering every child

That keeps the auth service from getting hammered by four simultaneous bootstrap flows, while still giving the rest of the system real gateway-driven load.

While the test is running, check:

- Grafana at `http://localhost:3000`
- Prometheus at `http://localhost:9090`
- cAdvisor at `http://localhost:8086`
- the `Smart Mobility Overview` dashboard for per-service request rate, error rate, latency, JVM heap, CPU, thread count, container CPU/memory, and downstream dependency latency

If your stack boots slowly, the runner waits for the gateway and auth readiness before starting and retries auth bootstrap calls internally. You can tune it with `--warmup-stagger-ms`, `--launch-stagger-ms`, `--readiness-timeout-ms`, and `--readiness-poll-ms`.

## Random Ride Load Simulator

If you want a single autonomous scenario that keeps creating new rides, randomly accepts or rejects driver assignments, and prints live activity counts, use:

```bash
node scripts/simulate-random-ride-load.mjs run \
  --gateway-url http://localhost:8080 \
  --run-id demo-random-load \
  --riders 4 \
  --driver-count 12 \
  --active-driver-count 6 \
  --accept-probability 0.7
```

What it does:
- boots multiple riders and one shared driver pool
- marks drivers online and keeps moving the active ones
- auto-accepts or auto-rejects each assignment with the configured probability
- starts a new ride automatically after the previous one completes
- prints a periodic summary line like:

```text
[load] activeRides=4 activeRiders=4 activeDrivers=12 completedRides=7 failedRides=1 acceptedAssignments=8 rejectedAssignments=4
```

The `--riders` flag controls how many rider loops run in parallel. All riders share the same driver pool, which makes the simulator more realistic because drivers are contested across rides instead of being reserved for only one rider.

If you want a fixed terminal dashboard instead of scrolling logs, add `--dashboard`. In dashboard mode the simulator redraws a compact screen with the live counters instead of printing every event line.

You can also use the interactive shell launcher:

```bash
./scripts/run-random-ride-load.sh
```

It prompts for the key values and then runs the simulator with those flags for you.
