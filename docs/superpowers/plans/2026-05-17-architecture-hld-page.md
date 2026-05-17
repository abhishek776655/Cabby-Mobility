# Architecture HLD Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `docs/architecture.html` with a standalone, accurate, polished HLD page containing system and data-flow diagrams for the Smart Mobility platform.

**Architecture:** Build a single static HTML document with embedded CSS and lightweight JavaScript. Use code/config facts gathered from services, controllers, Kafka producers/consumers, Docker Compose, and existing HLD docs. Avoid network dependencies so the page works through a direct `file://` open.

**Tech Stack:** HTML, CSS, vanilla JavaScript, local browser verification.

---

## File Structure

- Modify: `docs/architecture.html` - the standalone architecture dashboard and diagrams.
- Reference only: `docs/superpowers/specs/2026-05-17-architecture-hld-page-design.md` - approved design.
- Reference only: service `application.properties`, controllers, Kafka producers/consumers, and docs under `docs/HLD/`.

## Task 1: Replace Architecture Page Content

**Files:**
- Modify: `docs/architecture.html`

- [ ] **Step 1: Replace the existing HTML with a standalone dashboard**

Use a single HTML file with:

- Header summary and tech badges.
- Legend for REST, Kafka, WebSocket, storage, and discovery.
- System HLD diagram.
- Four data-flow diagrams.
- Service inventory.
- Event/storage map.
- Accuracy notes.

Include these code-derived facts:

```text
Ports:
Gateway 8080
Auth 8091
User 8081
Cab 8089
Driver 8084
Location 8090
Matchmaking 8087
Realtime 8095
Eureka 8761

Kafka topics:
user.created
ride-requested
assignment-accepted
assignment-rejected
driver-assigned
matchmaking-failed
driver-location-events
assignment-requested

WebSocket destinations:
/topic/trip/{rideId}
/topic/driver/{driverUserId}

Internal endpoints:
Auth -> User: /internal/users
Matchmaking -> Location: /internal/nearby
Cab -> Matchmaking: /internal/dispatch/{rideId}
```

- [ ] **Step 2: Keep diagrams responsive**

Use CSS grid/flexbox and horizontal flow wrappers so diagrams remain readable on desktop and mobile. Define stable node dimensions, wrap labels, and avoid nested cards.

- [ ] **Step 3: Add lightweight highlighting**

Add small buttons that highlight flow classes:

```javascript
function setFlow(filter) {
  document.documentElement.dataset.flow = filter;
}
```

Supported values: `all`, `rest`, `kafka`, `storage`, `ws`.

## Task 2: Verify Locally

**Files:**
- Read: `docs/architecture.html`

- [ ] **Step 1: Scan for external dependencies**

Run:

```bash
rg -n "https?://|fonts.googleapis|cdn|@import" docs/architecture.html
```

Expected: no output.

- [ ] **Step 2: Basic HTML sanity check**

Run:

```bash
wc -l docs/architecture.html
```

Expected: non-zero line count and command success.

- [ ] **Step 3: Open or inspect the page**

Use the browser companion or a local browser check to confirm the page loads and the main sections render:

```text
Expected visible sections:
Smart Mobility Platform Architecture
System HLD
Ride Request & Dispatch
Driver Response
Location & Realtime
Auth & Onboarding
Services
Events & Storage
Current Implementation Details
```

## Task 3: Final Review

**Files:**
- Review: `docs/architecture.html`

- [ ] **Step 1: Check service and event names**

Confirm the page contains exact names from the code:

```bash
rg -n "ride-requested|assignment-accepted|assignment-rejected|driver-assigned|matchmaking-failed|user.created|driver-location-events|assignment-requested|/internal/nearby|/internal/dispatch" docs/architecture.html
```

Expected: all listed terms appear.

- [ ] **Step 2: Check current implementation details**

Confirm the page explains these current facts:

```text
Current code/config defaults use auth 8091, cab 8089, location 8090, realtime 8095.
Gateway routes /rides/** and /dispatch/** to Cab Service.
Gateway routes /drivers/** to Driver Service.
Gateway guards /location/internal/nearby for internal nearby lookup.
```

- [ ] **Step 3: Report completion**

Summarize:

- `docs/architecture.html` replaced with the new HLD dashboard.
- Verification commands run.
- Any limitations, such as inability to use browser automation if sandboxed.
