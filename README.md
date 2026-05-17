# 🚗 Smart Mobility Platform

A production-grade, event-driven ride-hailing platform built with Spring Boot microservices architecture, designed for scalability, low-latency matchmaking, and strong consistency in ride lifecycle.

---

## 🏗️ Architecture Overview

```
Client → API Gateway → Microservices → Kafka → DB/Cache/Redis
```

- **API Gateway** (8080) - Request routing, JWT auth, rate limiting
- **Auth Service** (8082) - Authentication & JWT issuance
- **User Service** (8081) - User profile management
- **Cab Service** (8083) - Ride orchestration & state machine
- **Driver Service** (8084) - Driver management & availability
- **Realtime Gateway** (8085) - WebSocket/STOMP real-time updates
- **Location Service** (8086) - Driver location (Redis GEO)
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

Starts: PostgreSQL (5432), Redis (6379), Kafka (9092), Eureka (8761)

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

### Service Ports

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Auth Service | 8082 |
| User Service | 8081 |
| Cab Service | 8083 |
| Driver Service | 8084 |
| Realtime Gateway | 8085 |
| Location Service | 8086 |
| Matchmaking Service | 8087 |
| Eureka | 8761 |

---

## 🔑 Key Features

### Ride Lifecycle

```
REQUESTED → MATCHED → ACCEPTED → STARTED → COMPLETED → CANCELLED
```

### Real-time Communication

- WebSocket/STOMP via Realtime Gateway (8085)
- Rider trip tracking: Subscribe to `/topic/trip/{rideId}`
- Driver notifications: Subscribe to `/topic/driver/{driverId}`

### Event-Driven Design

Kafka topics for async communication:
- `ride-requested` - Trigger matching
- `driver-assigned` - Driver matched
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

> **Note:** `/location/nearby` is internal-only (used by Matchmaking Service)

### WebSocket
```
ws://localhost:8085/ws
STOMP: /topic/trip/{rideId}, /topic/driver/{driverId}
```

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