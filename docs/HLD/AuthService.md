# 🔐 AUTH SERVICE — HLD + LLD (Smart Mobility)

## 🏗️ High Level Design (HLD)

### 🎯 Purpose

Auth Service manages the **authentication and identity lifecycle**:

* User registration orchestration (Saga)
* Login & JWT issuance
* Refresh token lifecycle
* Token revocation
* Event publishing & consumption

---

## 📦 Responsibilities

### Core

* Authenticate users (login)
* Generate JWT (access + refresh tokens)
* Manage refresh tokens
* Orchestrate user creation (Saga)
* Handle token revocation (logout)
* Publish & consume auth events

### Boundaries

* ❌ No user profile management (handled by user-service)
* ❌ No business authorization logic
* ❌ No API routing (handled by gateway)

---

## 🔗 Inter-Service Communication

### Sync (REST)

* Gateway → Auth Service (user APIs)

### Async (Kafka)

**Produces:**

* user.create.requested
* auth.created
* user.rollback

**Consumes:**

* user.created

---

## 🧠 Auth Saga Flow

```
Auth → user.create.requested
        ↓
User Service → create user
        ↓
User → user.created
        ↓
Auth → save credentials
        ↓
Auth → auth.created

❌ On failure:
Auth → user.rollback → User deletes user
```

---

## 🗄️ Storage Strategy

### PostgreSQL

* Auth credentials
* Refresh tokens
* Token metadata

### (Optional Future)

* Redis (token blacklist)
* Outbox table (event reliability)

---

## ⚙️ High-Level Flow

### Register (Saga)

User → Auth → Kafka (user.create.requested)

---

### Login

User → Auth → validate credentials → generate JWT

---

### Refresh Token

User → Auth → validate refresh token → issue new access token

---

### Logout

User → Auth → revoke tokens

---

# 🧱 Low Level Design (LLD)

## 📁 Package Structure

```
auth-service/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
├── security/
├── jwt/
├── event/
├── producer/
├── consumer/
├── saga/
├── config/
├── exception/
```

---

## 🗄️ Database Schema

### auth_credentials

```sql
CREATE TABLE auth_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50),
    created_at TIMESTAMP
);
```

---

### refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token VARCHAR(500) UNIQUE NOT NULL,
    expiry_date TIMESTAMP,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP
);
```

---

### processed_events (Idempotency)

```sql
CREATE TABLE processed_events (
    event_id VARCHAR PRIMARY KEY,
    event_type VARCHAR(100),
    processed_at TIMESTAMP
);
```

---

## 🌐 APIs

### Register

POST /auth/register

---

### Login

POST /auth/login

---

### Refresh Token

POST /auth/refresh

---

### Logout

POST /auth/logout

---

### Revoke All Tokens

POST /auth/revoke-all

---

## ⚙️ Service Logic

### Register (Saga Start)

```java
publish user.create.requested;
```

---

### Handle User Created

```java
save auth credentials;
publish auth.created;
```

---

### Compensation (Rollback)

```java
publish user.rollback;
```

---

### Login

```java
validate password;
generate JWT;
store refresh token;
```

---

### Refresh Token

```java
validate refresh token;
generate new access token;
```

---

### Logout

```java
mark tokens as revoked;
```

---

## 📡 Kafka Events

### user.create.requested

```json
{
  "eventId": "...",
  "email": "...",
  "role": "USER"
}
```

---

### user.created

```json
{
  "eventId": "...",
  "userId": "...",
  "email": "..."
}
```

---

### user.rollback

```json
{
  "eventId": "...",
  "userId": "..."
}
```

---

### auth.created

```json
{
  "eventId": "...",
  "userId": "...",
  "status": "CREATED"
}
```

---

## ⚡ Kafka Flow

```
Auth → user.create.requested → User Service
User → user.created → Auth
Auth → auth.created
Auth → user.rollback (on failure)
```

---

## 🔒 Concurrency

### Idempotency

processed_events table prevents duplicate event processing

---

### Token Safety

Refresh tokens ensure controlled session lifecycle

---

## 🧠 Patterns Used

* Saga Pattern (core)
* Event-driven architecture (Kafka)
* Observer Pattern (Kafka consumers)
* Repository Pattern
* Service Layer Pattern
* Builder Pattern (API responses)

---

## ⚠️ Failure Handling

* Kafka retry
* DLQ (Dead Letter Queue)
* Idempotent consumers
* Compensation (rollback via events)

---

## 🔑 Key Insights

* Auth Service = **identity owner**
* Event-driven user creation
* No distributed transactions
* Compensation ensures consistency
* Designed for scalability & resilience
