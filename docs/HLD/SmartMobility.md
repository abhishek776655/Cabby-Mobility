# 🚗 Smart Mobility Platform

## 📌 Overview

A production-grade, event-driven ride-hailing platform designed for scalability, low latency matchmaking, and strong consistency in ride lifecycle.

---

# 📄 PRODUCT REQUIREMENTS DOCUMENT (PRD)

## 1. Product Vision

Build a scalable Smart Mobility system connecting riders and drivers with real-time matching, intelligent pricing, and high availability.

---

## 2. Objectives

### Primary

* Real-time ride booking and driver allocation
* Matchmaking latency < 2 seconds
* Strong ride lifecycle consistency

### Secondary

* Dynamic pricing
* Driver optimization
* Observability-first architecture

---

## 3. Personas

### Rider

* Book rides
* Track trips
* Make payments

### Driver

* Accept/reject rides
* Update availability
* Earn income

---

## 4. Core Features

### Authentication

* JWT-based auth
* Role-based access

### Ride Booking

* Create ride
* Driver assignment
* Lifecycle tracking

### Matchmaking

* Nearby driver discovery
* Ranking algorithm
* Retry logic

### Driver Management

* Availability
* Location updates

---

## 5. Ride Lifecycle

REQUESTED → MATCHING → DRIVER_ASSIGNED → ONGOING → COMPLETED

Terminal/alternate states: CANCELLED, NO_DRIVER_AVAILABLE

---

## 6. Non-Functional Requirements

* High scalability (horizontal)
* 99.9% availability
* API latency < 200ms
* Event-driven consistency

---

# 🏗️ HIGH LEVEL DESIGN (HLD)

## Service Ports

| Service | Port | Technology |
|---------|------|------------|
| Gateway | 8080 | Spring Cloud Gateway |
| Auth Service | 8091 | Spring Boot |
| User Service | 8081 | Spring Boot |
| Cab Service | 8089 | Spring Boot |
| Driver Service | 8084 | Spring Boot |
| Location Service | 8090 | Spring Boot + Redis |
| Matchmaking Service | 8087 | Spring Boot + Kafka |
| Realtime Gateway | 8095 | Spring Boot + WebSocket |
| PostgreSQL | 5432 | - |
| Redis | 6379 | - |
| Kafka | 9092 | - |

## Architecture Overview

Client → API Gateway → Microservices → Kafka + DB/Cache/Redis → WebSocket fanout

---

## Diagram
#### COMPONENT DIAGRAM (HLD VIEW)
```mermaid
flowchart TD

RiderApp["Rider App"] -->|"REST"| Gateway["API Gateway :8080"]
DriverApp["Driver App"] -->|"REST"| Gateway
RiderApp -->|"STOMP subscribe"| Realtime["Realtime Gateway :8095"]
DriverApp -->|"STOMP subscribe"| Realtime

Gateway -->|"auth routes"| Auth["Auth Service :8091"]
Gateway -->|"user routes"| User["User Service :8081"]
Gateway -->|"rides and dispatch routes"| Cab["Cab Service :8089"]
Gateway -->|"driver routes"| Driver["Driver Service :8084"]
Gateway -->|"location driver routes"| Location["Location Service :8090"]
Gateway -->|"matchmaking routes"| Matchmaking["Matchmaking Service :8087"]

Auth -->|"internal users API"| User
User -->|"user.created"| Kafka[("Kafka :9092")]
Kafka -->|"user.created"| Driver

Cab -->|"ride-requested"| Kafka
Kafka -->|"ride-requested"| Matchmaking
Matchmaking -->|"nearby drivers API"| Location
Location -->|"GEO and availability"| Redis[("Redis :6379")]
Matchmaking -->|"driver reservation and dispatch cache"| Redis
Matchmaking -->|"dispatch sessions and attempts"| Postgres[("PostgreSQL :5432")]
Cab -->|"rides"| Postgres
Auth -->|"credentials and refresh tokens"| Postgres
User -->|"users"| Postgres
Driver -->|"drivers"| Postgres

Matchmaking -->|"driver-assigned or matchmaking-failed"| Kafka
Kafka -->|"driver-assigned or matchmaking-failed"| Cab
Cab -->|"assignment-accepted or assignment-rejected"| Kafka
Kafka -->|"assignment response"| Matchmaking

Matchmaking -->|"assignment-requested"| Kafka
Kafka -->|"driver-location-events or assignment-requested"| Realtime
Realtime -->|"trip topic"| RiderApp
Realtime -->|"driver topic"| DriverApp

Gateway -.->|"service discovery"| Eureka["Eureka :8761"]
Auth -.->|"registers"| Eureka
User -.->|"registers"| Eureka
Cab -.->|"registers"| Eureka
Driver -.->|"registers"| Eureka
Location -.->|"registers"| Eureka
Matchmaking -.->|"registers"| Eureka
Realtime -.->|"registers"| Eureka
```

## Component Diagrams


#### SYSTEM FLOW (SEQUENCE)
```mermaid
sequenceDiagram
    participant Rider
    participant Gateway
    participant CabService
    participant Kafka
    participant MatchmakingService
    participant LocationService
    participant Redis
    participant RealtimeGateway
    participant Driver

    Rider->>Gateway: POST ride request
    Gateway->>CabService: Route to ride creation API
    CabService->>CabService: Persist ride as REQUESTED/MATCHING
    CabService->>Kafka: Publish ride-requested

    Kafka->>MatchmakingService: Consume ride-requested
    MatchmakingService->>LocationService: POST /internal/nearby
    LocationService->>Redis: GEOSEARCH drivers:available:geo
    Redis-->>LocationService: Nearby online driver ids
    LocationService-->>MatchmakingService: Candidate drivers

    MatchmakingService->>Redis: SETNX driver reservation with TTL
    MatchmakingService->>MatchmakingService: Persist dispatch session and attempt
    MatchmakingService->>Kafka: Publish assignment-requested

    Kafka->>RealtimeGateway: Consume assignment-requested
    RealtimeGateway->>Driver: STOMP /topic/driver/{driverUserId}

    Driver->>Gateway: POST /dispatch/driver-response
    Gateway->>CabService: Route driver response
    CabService->>Kafka: Publish assignment-accepted or assignment-rejected

    Kafka->>MatchmakingService: Consume driver response
    alt accepted
        MatchmakingService->>Kafka: Publish driver-assigned
        Kafka->>CabService: Consume driver-assigned
        CabService->>CabService: Move ride to DRIVER_ASSIGNED
    else rejected or timeout
        MatchmakingService->>Redis: Release reservation
        MatchmakingService->>MatchmakingService: Retry next candidate
    else exhausted
        MatchmakingService->>Kafka: Publish matchmaking-failed
        Kafka->>CabService: Consume matchmaking-failed
        CabService->>CabService: Move ride to NO_DRIVER_AVAILABLE
    end
```

#### AUTH + USER + DRIVER ONBOARDING FLOW
```mermaid
sequenceDiagram
    participant Client
    participant Gateway
    participant AuthService
    participant UserService
    participant Kafka
    participant DriverService
    participant Postgres

    Client->>Gateway: POST /auth/register or /auth/login
    Gateway->>AuthService: Route auth request
    AuthService->>UserService: POST /internal/users for registration
    UserService->>Postgres: Persist identity
    UserService->>Kafka: Publish user.created
    Kafka->>DriverService: Consume user.created
    DriverService->>Postgres: Create or update driver profile when applicable
    AuthService->>Postgres: Persist credentials / refresh tokens
    AuthService-->>Client: JWT access token + refresh token
```

#### RIDE STATE MACHINE

```mermaid
stateDiagram-v2
    [*] --> REQUESTED
    REQUESTED --> MATCHING: ride-requested published
    MATCHING --> DRIVER_ASSIGNED: driver-assigned consumed
    MATCHING --> NO_DRIVER_AVAILABLE: matchmaking-failed consumed
    DRIVER_ASSIGNED --> ONGOING: ride started
    ONGOING --> COMPLETED: ride completed
    REQUESTED --> CANCELLED: rider cancels
    MATCHING --> CANCELLED: rider cancels
    DRIVER_ASSIGNED --> CANCELLED: rider/driver cancels
    COMPLETED --> [*]
    CANCELLED --> [*]
    NO_DRIVER_AVAILABLE --> [*]
```

#### DRIVER STATE MACHINE
```mermaid
stateDiagram-v2
    [*] --> OFFLINE
    OFFLINE --> ONLINE
    ONLINE --> BUSY
    BUSY --> ONLINE
    ONLINE --> OFFLINE
```

#### END-TO-END FLOW (SIMPLIFIED)
```mermaid
flowchart TD

Rider["Rider App"] -->|"request ride"| Gateway["API Gateway"]
Gateway -->|"route ride API"| CabService["Cab Service"]

CabService -->|"persist ride"| Postgres[("PostgreSQL")]
CabService -->|"ride-requested"| Kafka[("Kafka")]
Kafka -->|"consume ride request"| Matchmaking["Matchmaking Service"]

Matchmaking -->|"nearby drivers"| LocationService["Location Service"]
LocationService -->|"GEO search available drivers"| Redis[("Redis")]
Matchmaking -->|"reserve driver and cache dispatch"| Redis
Matchmaking -->|"persist dispatch session"| Postgres

Matchmaking -->|"assignment-requested"| Kafka
Kafka -->|"assignment event"| RealtimeGateway["Realtime Gateway"]
RealtimeGateway -->|"driver topic"| Driver["Driver App"]

Driver -->|"driver response API"| Gateway
Gateway -->|"route dispatch API"| CabService
CabService -->|"assignment accepted or rejected"| Kafka
Kafka -->|"consume driver response"| Matchmaking

Matchmaking -->|"driver-assigned or matchmaking-failed"| Kafka
Kafka -->|"final dispatch outcome"| CabService
CabService -->|"update ride status"| Postgres
RealtimeGateway -->|"trip topic"| Rider
```
# 🧩 SERVICES & RESPONSIBILITIES

## 1. API Gateway

### Responsibilities

* Request routing
* JWT Authentication validation
* Role-based authorization (ADMIN, DRIVER, RIDER)
* Rate limiting (Redis)
* Circuit breaker (Resilience4j)

### Communication

* Sync → All services

### Routes

| Path | Service | Port | Roles Allowed |
|------|---------|------|---------------|
| /auth/** | auth-service | 8091 | Public auth APIs |
| /users/** | user-service | 8081 | Configured gateway route |
| /cab/**, /rides/**, /dispatch/** | cab-service | 8089 | Cab and ride APIs |
| /driver/**, /drivers/** | driver-service | 8084 | Driver APIs |
| /location/driver/online | location-service | 8090 | DRIVER |
| /location/driver/offline | location-service | 8090 | DRIVER |
| /location/driver/update | location-service | 8090 | DRIVER |
| /location/internal/nearby | location-service | 8090 | INTERNAL header required by Gateway |
| /matchmaking/** | matchmaking-service | 8087 | Configured gateway route |

### Service Controller Paths

| Service | Controller Paths |
|---------|------------------|
| Auth Service | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/logout-all` |
| User Service | `/internal/users`, `/internal/users/{id}` |
| Cab Service | `/rides`, `/rides/{rideId}`, `/rides/{rideId}/cancel`, `/rides/{rideId}/start`, `/rides/{rideId}/complete`, `/dispatch/driver-response`, `/dispatch/cancel`, `/dispatch/{rideId}` |
| Driver Service | `/drivers`, `/drivers/{userId}` |
| Location Service | `/location/driver/online`, `/location/driver/offline`, `/location/driver/update`, `/location/internal/nearby` |
| Matchmaking Service | `/internal/dispatch/{rideId}` |
| Realtime Gateway | `/realtime/info`, WebSocket/STOMP topics `/topic/trip/{rideId}` and `/topic/driver/{driverUserId}` |

---

## 2. Auth Service

### Responsibilities

* Login/Register
* JWT issuance
* Credential storage

### Communication

* Calls → User Service `/internal/users`
* Persists → auth credentials, refresh tokens

---

## 3. User Service

### Responsibilities

* User profile management
* Role management

### Communication

* Emits → user.created
* Sync APIs for reads

---

## 4. Cab Service (CORE - Orchestrator)

### Responsibilities

* Ride creation & state machine
* Persist rides
* **Dispatch APIs** - Handle driver response, cancel, status queries
* Publish driver response events to Kafka

### Communication

* Emits → ride-requested, assignment-accepted, assignment-rejected
* Consumes → driver-assigned, matchmaking-failed

---

## 5. Driver Service

### Responsibilities

* Driver onboarding
* Driver profile lookup
* Driver metadata persistence

### Communication

* Consumes → user.created
* Persists → driver profile data

---

## 6. Matchmaking Service (CORE INTELLIGENCE - INTERNAL)

### Responsibilities

* Find nearby drivers via Location Service
* Rank drivers
* Reserve drivers using Redis TTL locks
* Coordinate assignment accepted/rejected events
* Handle retry on driver rejection or timeout

### Communication

* Consumes → ride-requested, assignment-accepted, assignment-rejected
* Calls → Location Service `/internal/nearby`
* Persists → dispatch sessions, assignment attempts, processed events
* Redis → driver reservations and dispatch cache
* Emits → driver-assigned, matchmaking-failed, assignment-requested

> ⚠️ **No user-facing APIs** - triggered via Kafka events only

---

## 7. Location Service

### Responsibilities

* Real-time driver location (Redis GEO)
* Driver availability tracking
* Nearby driver queries for matchmaking

### Communication

* Sync → Matchmaking Service
* Redis for spatial data
* Realtime integration topic → driver-location-events

---

## 8. Realtime Gateway Service

### Responsibilities

* Event fanout via WebSocket/STOMP
* Rider trip tracking (driver location broadcasts)
* Driver assignment notifications
* Stateless, no persistence

### Communication

* Consumes → driver-location-events, assignment-requested (Kafka)
* WebSocket → Rider App, Driver App

---

## 9. Pricing Service (Future)

### Responsibilities

* Fare calculation
* Surge pricing

---

## 9. Payment Service (Future)

### Responsibilities

* Payment processing

---

# 🔗 INTER-SERVICE COMMUNICATION

## Synchronous (REST)

* Gateway → Services
* Auth Service → User Service (`/internal/users`)
* Matchmaking Service → Location Service (`/internal/nearby`)
* Cab Service → Matchmaking Service (`/internal/dispatch/{rideId}`)

## Asynchronous (Kafka)

### Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| ride-requested | Cab Service | Matchmaking | Trigger driver matching |
| driver-assigned | Matchmaking | Cab Service | Driver successfully assigned |
| matchmaking-failed | Matchmaking | Cab Service | No driver available |
| assignment-accepted | Cab Service | Matchmaking | Driver accepted (from /dispatch/driver-response) |
| assignment-rejected | Cab Service | Matchmaking | Driver rejected → retry |
| driver-location-events | Location/event pipeline | Realtime Gateway | Driver location updates for rider tracking |
| assignment-requested | Matchmaking Service | Realtime Gateway | Driver assignment notifications |

Other:
* user.created

### Key Flows

**Ride Booking Flow:**
1. Cab Service → ride-requested → Kafka
2. Matchmaking consumes ride-requested
3. Matchmaking → Location Service (nearby drivers)
4. Location Service → Redis GEO (online available drivers)
5. Matchmaking reserves driver with Redis TTL lock
6. Matchmaking → assignment-requested → Kafka
7. Realtime Gateway broadcasts to driver topic
8. Driver → Cab Service `/dispatch/driver-response`
9. Cab Service → assignment-accepted or assignment-rejected → Kafka
10. Matchmaking publishes driver-assigned or matchmaking-failed
11. Cab Service consumes final outcome and updates ride state

**Auth/User/Driver Onboarding Flow:**
1. Client → Gateway → Auth Service
2. Auth Service → User Service `/internal/users`
3. User Service persists identity
4. User Service → user.created → Kafka
5. Driver Service consumes user.created and creates/updates driver profile when applicable

**Realtime Flow:**
1. Realtime Gateway consumes driver-location-events and assignment-requested
2. Rider App subscribes to `/topic/trip/{rideId}`
3. Driver App subscribes to `/topic/driver/{driverUserId}`

---

# 🧠 DESIGN PATTERNS USED

## 1. Microservices Architecture

* Independent services

## 2. API Gateway Pattern

* Central entry point

## 3. Saga Pattern (Kafka)

* Distributed consistency

## 4. Event-Driven Architecture

* Loose coupling via Kafka

## 5. State Machine Pattern

* Ride lifecycle enforcement

## 6. Circuit Breaker

* Resilience (Resilience4j)

## 7. Retry Pattern

* Fault tolerance

## 8. Caching Pattern (Redis)

* Fast driver lookup

## 9. CQRS (Future)

* Separate read/write paths

## 10. Role-Based Access Control (Gateway)

* JWT contains user roles
* Gateway validates role vs requested path
* Services trust X-User-Id headers from gateway

### Roles & Permissions

| Role | Access Paths |
|------|---------------|
| ADMIN | All paths |
| DRIVER | Driver profile routes, location driver routes, dispatch driver response |
| RIDER | User routes, ride routes, dispatch status/cancel routes |
| INTERNAL SERVICE | Internal dispatch and nearby-driver lookup routes |

---

# 🗄️ DATA LAYER

## PostgreSQL

* Strong consistency
* `auth_db` for credentials and refresh tokens
* `user_db` for identity data
* `cab_db` for rides and processed events
* `driver_db` for driver profiles
* `matchmaking_db` for dispatch sessions, assignment attempts, processed events

## Redis

* Gateway rate limiting
* Driver location GEO indexes
* Driver availability set
* Matchmaking driver reservations and dispatch cache

---

# 📊 OBSERVABILITY (Planned)

* Prometheus (metrics)
* Grafana (dashboards)
* OpenTelemetry (tracing)

---

# 🚀 DEPLOYMENT

* Docker (current)
* Kubernetes (future)

---

# 📌 KEY ARCHITECTURAL DECISIONS

1. Kafka-first async dispatch flow → scalability
2. Cab Service as ride source of truth → lifecycle consistency
3. Matchmaking isolated → independent scaling and retry control
4. Redis for real-time location and reservation state → low latency
5. Realtime Gateway stateless fanout → independent WebSocket scaling

---

# 🧭 WHY THIS DESIGN

* Prevents tight coupling
* Handles high concurrency
* Enables independent scaling
* Supports future extensions (delivery, logistics)

---

**Status:** Actively under development (microservice-by-microservice build)

---
