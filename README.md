# 🚗 Smart Mobility Platform

A production-grade, event-driven ride-hailing platform built with Spring Boot microservices architecture, designed for scalability, low-latency matchmaking, and strong consistency in ride lifecycle.

---

## 🏗️ Architecture Overview

```
Client → API Gateway → Microservices → Kafka → DB/Cache/Redis
```

- **API Gateway** (8080) - Request routing, JWT auth, rate limiting
- **Auth Service** (8091) - Authentication & JWT issuance
- **User Service** (8081) - User profile management
- **Cab Service** (8089) - Ride orchestration & state machine
- **Driver Service** (8084) - Driver management & availability
- **Realtime Gateway** (8095) - WebSocket/STOMP real-time updates
- **Location Service** (8090) - Driver location (Redis GEO)
- **Matchmaking Service** (8087) - Driver matching algorithm
- **Eureka Service** - Service discovery

---

## 🚀 Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Compose

### Infrastructure Setup

```bash
cd docker
docker-compose up -d
```

Starts: PostgreSQL (5432), Redis (6379), Kafka (9092), Eureka HA pair (8761 + 8762)

> **Note:** compose variable substitution (the `${VAR}` in port mappings) needs `.env` visible to the
> Compose CLI itself, not just to containers via `env_file:`. Since `.env` lives at the repo root, run from
> there with `--env-file`, or the affected ports fall back to their literal defaults instead of failing —
> `docker compose -f docker/docker-compose.yml --env-file .env up -d`.

### Run Services

```bash
# Option 1: Docker Compose (recommended)
cd docker
docker-compose up --build

# Option 2: Run individual services manually
cd auth-service && mvn spring-boot:run
cd user-service && mvn spring-boot:run
cd cab-service && mvn spring-boot:run
cd driver-service && mvn spring-boot:run
cd location-service && mvn spring-boot:run
cd matchmaking-service && mvn spring-boot:run
cd realtime-gateway-service && mvn spring-boot:run
cd gateway-service && mvn spring-boot:run
cd eureka-service && mvn spring-boot:run

# Option 3: Use IDE (IntelliJ/Eclipse)
# Import as Maven projects and run each service
```

### Pressure Testing

To intentionally constrain the Java services and overfill them with a small number of simulated users, use the pressure Compose overlay and the parallel simulator runner:

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.pressure.yml up -d
```

```bash
node scripts/load-test-scenarios.mjs --scenarios 4 --duration-ms 60000
```

The runner first warms up auth accounts sequentially, then launches the ride scenarios in parallel while reusing the warmed auth session state. That reduces auth-service noise and makes the pressure test much smoother.

Open these while the test is running:

- Grafana: `http://localhost:3000` (admin / `admin`, unless you override `GRAFANA_ADMIN_PASSWORD`)
- Prometheus: `http://localhost:9090`
- cAdvisor: `http://localhost:8086`
- Dashboard: `Smart Mobility Overview`

### Random Ride Load Simulator

If you want one autonomous scenario that keeps creating new rides, randomly accepts or rejects driver assignments, and prints live activity counts in the terminal, use:

```bash
node scripts/simulate-random-ride-load.mjs run \
  --gateway-url http://localhost:8080 \
  --run-id demo-random-load \
  --riders 4 \
  --driver-count 12 \
  --active-driver-count 6 \
  --accept-probability 0.7
```

This script keeps the load flowing by:
- booting multiple riders and a shared driver pool
- moving the active drivers around the pickup area
- auto-accepting or auto-rejecting each assignment with the configured probability
- starting a new ride immediately after the previous one completes
- printing a summary like `activeRides=4 activeRiders=4 activeDrivers=12`

Add `--dashboard` if you want a fixed terminal view that redraws in place instead of printing every event line.

You can also use the interactive launcher:

```bash
./scripts/run-random-ride-load.sh
```

### Service Ports

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Auth Service | 8091 |
| User Service | 8081 |
| Cab Service | 8089 |
| Driver Service | 8084 |
| Realtime Gateway | 8095 |
| Location Service | 8090 |
| Matchmaking Service | 8087 |
| Eureka (peer1) | 8761 |
| Eureka (peer2) | 8762 |

---

## 🔑 Key Features

### Ride Lifecycle

```
MATCHING → DRIVER_ASSIGNED → ONGOING → COMPLETED → CANCELLED

MATCHING → NO_DRIVER_AVAILABLE
```

### Real-time Communication

- WebSocket/STOMP via Realtime Gateway (8095)
- Rider trip tracking: Subscribe to `/topic/trip/{rideId}`
- Driver notifications: Subscribe to `/topic/driver/{driverId}`

### Event-Driven Design

Kafka topics for async communication:
- `ride-requested` - Trigger matching
- `driver-assigned` - Driver matched
- `matchmaking-failed` - No driver available
- `driver-location-events` - Real-time location
- `assignment-requested` - Driver assignment notification

---

## 📁 Project Structure

```
smart-mobility/
├── auth-service/         # Authentication & JWT
├── user-service/        # User profiles
├── cab-service/         # Ride orchestration
├── driver-service/      # Driver management
├── location-service/    # Driver locations (Redis)
├── matchmaking-service/ # Driver matching
├── realtime-gateway-service/ # WebSocket/STOMP
├── gateway-service/     # API Gateway
├── eureka-service/      # Service discovery
├── docker/              # Docker Compose
└── docs/               # HLD/LLD documentation
```

---

## 📖 Documentation

- [Architecture HLD](docs/HLD/SmartMobility.md)
- [Service HLDs](docs/HLD/)
  - [Auth Service](docs/HLD/AuthService.md)
  - [User Service](docs/HLD/UserService.md)
  - [Cab Service](docs/HLD/CabService.md)
  - [Driver Service](docs/HLD/DriverService.md)
  - [Location Service](docs/HLD/LocationService.md)
  - [Matchmaking Service](docs/HLD/MatchmakingService.md)
  - [Realtime Service](docs/HLD/RealtimeService.md)

---

## 🧪 API Endpoints

### Auth
```
POST /auth/login
POST /auth/register
```

### Rides
```
POST /rides/create
GET /rides/{rideId}
POST /dispatch/driver-response
```

### Driver
```
POST /driver/online
POST /driver/offline
```

### Location (Driver only)
```
POST /location/driver/online
POST /location/driver/offline
POST /location/driver/update
```

> **Note:** `/internal/nearby` is internal-only (used by Matchmaking Service) and requires an
> `X-Internal-Secret` header — the service verifies this itself, not just the gateway's edge block.

### WebSocket
```
ws://localhost:8095/ws
STOMP: /topic/trip/{rideId}, /topic/driver/{driverId}
```

> **Auth required:** the STOMP `CONNECT` frame must include a native header
> `Authorization: Bearer {jwt}` (same token from `/auth/login`), or the connection is rejected. `SUBSCRIBE`
> is scoped to the caller's own topics — a rider/driver can only subscribe to their own `driver`/`trip`
> topic (verified against Cab Service for trip topics), not anyone else's.

---

## 🛠️ Technology Stack

- **Framework:** Spring Boot 4.0.5
- **Language:** Java 21
- **Build:** Maven
- **Database:** PostgreSQL
- **Cache:** Redis
- **Message Broker:** Kafka
- **Service Discovery:** Eureka
- **API Gateway:** Spring Cloud Gateway

---

## 📝 License

MIT License

---

**Status:** Active development - Microservices built incrementally
