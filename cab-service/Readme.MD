# 🚕 Cab Service — Smart Mobility Platform

## 📌 Overview

The **Cab Service** manages the **ride lifecycle** using a strict **state machine architecture**. It is designed as a **low-frequency, high-consistency service** within a larger event-driven microservices ecosystem.

---

## 🧠 Core Responsibility

```
Cab Service = Ride Lifecycle State Manager
```

### ✅ Owns

* Ride creation
* State transitions
* Persistence

### ❌ Does NOT Own

* Driver matching
* Location tracking
* Notifications
* Payments

---

# 🏗️ Architecture Principles

* Microservices
* Stateless services
* Database per service
* Clean architecture:

  ```
  Controller → Service → State → Repository
  ```
* Event-driven (Kafka - future)

---

# 📁 Project Structure

```
com.smartmobility.cabservice

├── controller
├── service
│   └── impl
├── repository
├── entity
├── dto
├── mapper
├── state
├── exception
├── config
```

---

# 🧠 Design Patterns

| Pattern         | Purpose                 |
| --------------- | ----------------------- |
| State Pattern   | Ride lifecycle          |
| Factory Pattern | State resolution        |
| Repository      | Persistence abstraction |
| DTO             | API contracts           |
| Mapper          | Data conversion         |
| Service Layer   | Orchestration           |

---

# 🧱 Low-Level Design (LLD)

## 📌 Core Classes

```
+----------------------+
|      RideEntity      |
+----------------------+
| id: UUID             |
| riderId: UUID        |
| driverId: UUID       |
| pickupLocation: str  |
| dropLocation: str    |
| status: RideStatus   |
| fare: double         |
| createdAt            |
| updatedAt            |
+----------------------+
```

---

## 📌 Enum

```
RideStatus
-----------
REQUESTED
MATCHING
DRIVER_ASSIGNED
ONGOING
COMPLETED
CANCELLED
```

---

## 📌 Service Layer

```
RideService (interface)
-----------------------
+ createRide()
+ getRideById()
+ cancelRide()
+ startRide()
+ completeRide()

RideServiceImpl
-----------------------
- orchestrates calls
- uses state machine
```

---

## 📌 Repository

```
RideRepository
--------------
extends JpaRepository<RideEntity, UUID>
```

---

## 📌 State Machine

```
RideState (interface)
---------------------
+ next(RideEntity ride)
+ cancel(RideEntity ride)
```

---

### Concrete States

```
RequestedState
MatchingState
DriverAssignedState
OngoingState
CompletedState
CancelledState
```

---

## 📌 Factory

```
RideStateFactory
----------------
+ getState(RideStatus status)
```

---

## 📌 Mapper

```
RideMapper
-----------
+ toEntity()
+ toResponseDTO()
```

---

# 🧩 Class Diagram (Combined View)

```
                +------------------+
                |   RideController |
                +------------------+
                         |
                         v
                +------------------+
                |   RideService    |<----------------------+
                +------------------+                       |
                         |                                 |
                         v                                 |
                +------------------+                       |
                | RideServiceImpl  |                       |
                +------------------+                       |
                         |                                 |
         +---------------+--------------+                  |
         |                              |                  |
         v                              v                  |
+------------------+          +--------------------+       |
| RideStateFactory |          |  RideRepository    |       |
+------------------+          +--------------------+       |
         |                                                |
         v                                                |
+----------------------+
|     RideState        |
+----------------------+
         |
         +------------------------------------------------------+
         |        |            |           |         |           |
         v        v            v           v         v           v
 Requested Matching DriverAssigned Ongoing Completed Cancelled
   State     State       State        State     State     State

```

---

# 🔄 Ride Lifecycle

```
REQUESTED → MATCHING → DRIVER_ASSIGNED → ONGOING → COMPLETED
                         ↘
                       CANCELLED
```

---

## 📌 Transition Rules

| From            | To                         |
| --------------- | -------------------------- |
| REQUESTED       | MATCHING, CANCELLED        |
| MATCHING        | DRIVER_ASSIGNED, CANCELLED |
| DRIVER_ASSIGNED | ONGOING, CANCELLED         |
| ONGOING         | COMPLETED                  |

---

# 🔁 Flow Diagrams

---

## 🚀 Create Ride

```
Client → Gateway → Cab Service
       → Controller → Service → DB
```

---

## 🔍 Matching (Future)

```
Cab Service → Kafka → matchmaking-service
```

---

## 🚗 Driver Assignment

```
matchmaking-service → Cab Service → DB update
```

---

## ▶️ Ride Start

```
Driver App → Cab Service → State Change
```

---

## 🛑 Ride Complete

```
Driver App → Cab Service → COMPLETED
```

---

## ❌ Cancellation

```
Client/Driver → Cab Service → CANCELLED
```

---

# 📡 API Specifications

---

## POST /rides

Create Ride

```
Request:
{
  "riderId": "uuid",
  "pickupLocation": "...",
  "dropLocation": "..."
}
```

---

## GET /rides/{id}

Fetch Ride

---

## POST /rides/{id}/cancel

Cancel Ride

---

## POST /rides/{id}/start

Start Ride

---

## POST /rides/{id}/complete

Complete Ride

---

# 📦 API Response Format

```
{
  "timestamp": "...",
  "status": 200,
  "data": {}
}
```

---

# ⚠️ Error Handling

* Global Exception Handler
* Custom Exceptions

---

# 🧠 Key Design Insights

### 1. State Machine is Core

All business rules live in states.

---

### 2. Service = Orchestrator

No business logic inside service.

---

### 3. Strict Boundaries

Cab Service does NOT handle:

* location
* matching
* payments

---

### 4. Scalability

* Stateless design
* Async ready (Kafka)
* Horizontal scaling

---

# 🧪 Testing

* Unit tests with Mockito
* Service layer focus
* Mock repository

---

# 🔮 Future Scope

* Kafka (Observer pattern)
* Fare Strategy
* Notification integration
* Matchmaking integration

---

# 🧠 Mental Model

```
Finite State Machine + Persistence
```

---

# 🚀 Status

* Design Complete ✅
* Implementation Starting 🚧

---
