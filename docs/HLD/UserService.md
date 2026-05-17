# 👤 USER SERVICE — HLD + LLD (Mobility System)

## Service Configuration

* **Port:** 8081
* **Database:** PostgreSQL

---

# 🏗️ High Level Design (HLD)

## 🎯 Purpose

User Service manages the **user identity lifecycle**:

* User creation & persistence
* Identity data management (email, phone, roles)
* Event publishing for downstream services
* Serving user data to internal services

---

## 📦 Responsibilities

### Core

* Create user (via Auth Service)
* Store identity data (email, phone, roles)
* Ensure uniqueness (email/phone)
* Publish user lifecycle events
* Provide user data to internal services

---

### Boundaries

* ❌ No authentication (handled by auth-service)
* ❌ No driver domain logic (handled by driver-service)
* ❌ No business workflows (handled by cab/matchmaking)
* ❌ No authorization enforcement (handled by gateway)

### Authorization

* Services trust X-User-Id and X-User-Role headers from gateway
* Resource-level checks implemented at service level (e.g., user owns resource)

---

## 🔗 Inter-Service Communication

### Sync (REST)

* Auth Service → User Service (user creation)
* Internal services → User Service (user lookup)

---

### Async (Kafka)

**Produces:**

```
user.created
```

**Consumes:**

```
(none currently)
```

---

## 🧠 User Creation Flow

```
Auth → (Feign) → User Service
        ↓
User Service → save user (DB)
        ↓
User Service → publish user.created (Kafka)
        ↓
Driver Service → consumes event
```

---

## 🗄️ Storage Strategy

### PostgreSQL

* User identity data
* Roles & status

---

### (Optional Future)

* Redis (caching user lookups)
* Outbox table (event reliability)

---

## ⚙️ High-Level Flow

### Create User

Auth → User Service → DB save → Kafka event

---

### Get User

Internal Service → User Service → DB lookup

---

# 🧱 Low Level Design (LLD)

---

## 📁 Package Structure

```
user-service/
├── controller/
├── service/
├── service/impl/
├── repository/
├── entity/
├── dto/
├── mapper/
├── event/
├── producer/
├── config/
├── exception/
```

---

## 🗄️ Database Schema

### users

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20) UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    status VARCHAR(50),
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

---

### user_roles

```sql
CREATE TABLE user_roles (
    user_id BIGINT,
    role VARCHAR(50),
    PRIMARY KEY (user_id, role)
);
```

---

### processed_events (Future Idempotency)

```sql
CREATE TABLE processed_events (
    event_id VARCHAR PRIMARY KEY,
    event_type VARCHAR(100),
    processed_at TIMESTAMP
);
```

---

## 🌐 APIs

### Create User (Internal)

POST /internal/users

---

### Get User

GET /users/{id}

---

### Get User by Email (Optional)

GET /users/email/{email}

---

## ⚙️ Service Logic

---

### Create User

```java
validate uniqueness (email/phone);
save user in DB;

try {
    publish user.created event;
} catch (Exception e) {
    log error (do NOT fail request);
}

return user;
```

---

### Get User

```java
fetch user by id;
return DTO;
```

---

## 📡 Kafka Events

---

### user.created

```json
{
  "eventId": "...",
  "userId": 123,
  "email": "test@gmail.com",
  "roles": ["RIDER", "DRIVER"]
}
```

---

## ⚡ Kafka Flow

```
User Service → user.created → Driver Service
```

---

## 🔒 Concurrency

### Idempotency (Future)

```
processed_events table will prevent duplicate event handling
```

---

## 🧠 Patterns Used

* Event-driven architecture (Kafka)
* Repository Pattern
* Service Layer Pattern
* Builder Pattern (DTOs)

---

## ⚠️ Failure Handling

* Kafka publish failure does NOT break user creation
* Retry handled at Kafka level
* Future: Outbox pattern for guaranteed delivery

---

## 🔑 Key Insights

* User Service = **identity owner (source of tru
