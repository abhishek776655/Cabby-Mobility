# 🔐 AUTH SERVICE — HLD + LLD (Smart Mobility)

## Service Configuration

* **Port:** 8091
* **Database:** PostgreSQL
* **Security:** Spring Security + JWT

## 🏗️ High Level Design (HLD)

### 🎯 Purpose

Auth Service manages the **authentication and identity lifecycle**:

* User registration — owns the credential record itself, publishes `auth.registered` for downstream
  identity/profile creation (outbox pattern, not a saga with compensation — see below)
* Login & JWT issuance
* Refresh token lifecycle
* Token revocation
* Event publishing (no Kafka consumers — this service never consumes)

---

## 📦 Responsibilities

### Core

* Authenticate users (login)
* Generate JWT (access + refresh tokens)
* Manage refresh tokens
* Own the credential record; publish `auth.registered` for user-service to pick up (outbox, not saga)
* Handle token revocation (logout)
* Publish auth events (via outbox relay — no consumers)

### Boundaries

* ❌ No user profile management (handled by user-service)
* ❌ No business authorization logic
* ❌ No API routing (handled by gateway)

### Security Configuration

* Spring Security with JWT Authentication Filter
* Stateless session management
* Role-based endpoint protection:
  - `/auth/**` - Public (permitAll)
  - `/admin/**` - ADMIN role only
  - `/driver/**` - DRIVER role only
  - Other endpoints - Authenticated users
* **Internal service-to-service calls** (`UserServiceClient` → user-service `/internal/users`) now attach
  an `X-Internal-Secret` header via a Feign `RequestInterceptor` (`FeignInternalAuthConfig`) — user-service
  itself verifies this header, not just the API Gateway's path-block
* Refresh tokens are hashed at rest (SHA-256) — see `refresh_tokens` schema below

---

## 🔗 Inter-Service Communication

### Sync (REST)

* Gateway → Auth Service (user APIs)
* Auth Service → User Service (`UserServiceClient` Feign) — **only** for `findByEmail`/`findByUserId`
  lookups during login/refresh, never for creating a user

### Async (Kafka)

**Produces:**

* auth.registered (via transactional outbox, not a direct `kafkaTemplate.send`)

**Consumes:**

* *(none — Auth Service has no `@KafkaListener` anywhere; it's a pure producer)*

---

## 🧠 Actual Registration Flow (Outbox, not Saga)

> ⚠️ **Correction:** earlier versions of this doc described registration as a saga with an explicit
> `user.create.requested` → `user.created` round-trip and a `user.rollback` compensation step. That was
> never built. The real flow below is simpler and has no compensation step because there's nothing to
> compensate — Auth Service commits its own row and never blocks on downstream services.

```
Register request
        ↓
Auth Service saves AuthCredential directly (auth_db is the identity's system of record for credentials)
        ↓
Auth Service writes an outbox row (topic="auth.registered") in the SAME transaction as the credential save
        ↓
OutboxRelayScheduler (polls every 1s) relays the row to Kafka, marks it processed
        ↓
User Service's AuthRegisteredConsumer (idempotent — checks existsById first) creates the User Service profile
        ↓
User Service publishes user.created → Driver Service / Rider Service create their own profiles
```

No rollback path exists or is needed: the credential row is authoritative and already committed: user-service
profile creation is downstream and eventually consistent, not a precondition for registration to succeed.

---

## 🗄️ Storage Strategy

### PostgreSQL

* Auth credentials
* Refresh tokens (hashed, see schema below)
* `outbox_events` — **implemented**, not future. Every Kafka publish from this service (`auth.registered`)
  goes through this table in the same transaction as the triggering write, relayed by `OutboxRelayScheduler`

### (Optional Future)

* Redis (token blacklist)

---

## ⚙️ High-Level Flow

### Register (Outbox, not Saga)

User → Auth → save credential + outbox row (same transaction) → OutboxRelayScheduler → Kafka (auth.registered)

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
├── entity/          (AuthCredential, RefreshToken, OutboxEvent)
├── dto/
├── mapper/
├── security/
├── util/            (JwtUtil)
├── client/          (UserServiceClient — Feign, lookup-only)
├── scheduler/       (OutboxRelayScheduler)
├── config/          (FeignInternalAuthConfig, SecurityBeanConfig)
├── exception/
```

> No `saga/`, `producer/`, or `consumer/` packages exist — this service has no Kafka consumers at all,
> and its one outbound event (`auth.registered`) goes through the outbox table + scheduler above rather
> than a dedicated producer/saga class.

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
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    token_hash VARCHAR(255) UNIQUE NOT NULL,  -- SHA-256 hex digest, NOT the raw token
    expiry_date TIMESTAMP,
    revoked BOOLEAN DEFAULT FALSE
);
```

> 🔒 **Security fix:** the raw refresh token used to be stored directly (`token` column) — a DB leak would
> hand out directly usable tokens. Now only a SHA-256 hash is persisted (`token_hash`). The raw token exists
> only in-memory, on the `RefreshToken` entity's `@Transient token` field, populated right after `create()`
> generates it so the caller can hand it to the client — it's never written to the database. `validate()`/
> `revoke()` hash the incoming raw token and look up by `token_hash`. SHA-256 (not bcrypt) is intentional:
> the token is already 128 bits of random entropy (`UUID.randomUUID()`), so this isn't a low-entropy secret
> needing slow-hash defense — it just needs to not sit in plaintext, plus a fast deterministic lookup.

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

### Register

```java
// existsByEmail → existing user re-login path (return existing credential's token), else:
save AuthCredential (userId = generated id, in the same row);
writeOutboxEvent(topic = "auth.registered", payload = {id, email, roles});  // same transaction as the save above
generateJwt(); createRefreshToken();
return AuthResponseDTO;
```

No separate "handle user created" or compensation/rollback step exists in this service — the credential
row is committed and authoritative on its own; user-service profile creation happens downstream via the
outbox-relayed event and is never a precondition for `register()` to succeed.

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

### auth.registered (Produced — only event this service publishes)

```json
{
  "id": 12345,
  "email": "rider@example.com",
  "roles": ["RIDER"]
}
```
> Relayed from the `outbox_events` table by `OutboxRelayScheduler`, not published directly from
> `AuthServiceImpl` — the write to `outbox_events` and the `AuthCredential` save happen in the same
> `@Transactional` method, so the event can't be lost if the credential commit fails (or vice versa).

**Consumed downstream by:** `user-service`'s `AuthRegisteredConsumer` (`topics = "auth.registered"`),
which is idempotent (checks `existsById` before inserting) and, on success, publishes `user.created` for
`driver-service`/`rider-service` to pick up.

---

## ⚡ Kafka Flow

```
Auth Service → auth.registered → (via outbox) → Kafka
Kafka → auth.registered → User Service (AuthRegisteredConsumer, idempotent)
User Service → user.created → Kafka
Kafka → user.created → Driver Service / Rider Service (create profile)
```

No rollback/compensation event exists — Auth Service's own write already committed and is never undone
based on what happens downstream.

---

## 🔒 Concurrency

### Idempotency

Auth Service has no `processed_events` table — it never consumes events, so there's nothing to dedupe on
its side. Idempotency for the registration chain lives downstream, in `user-service`'s
`AuthRegisteredConsumer` (`existsById` check before insert) and similarly in `driver-service`/`rider-service`'s
`user.created` consumers.

---

### Token Safety

Refresh tokens ensure controlled session lifecycle

---

## 🧠 Patterns Used

* Transactional Outbox Pattern (core) — not a saga; no compensation step exists
* Event-driven architecture (Kafka) — producer-only, no consumers in this service
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
