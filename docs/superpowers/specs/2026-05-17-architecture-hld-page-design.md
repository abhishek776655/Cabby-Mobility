# Architecture HLD Page Design

## Goal

Update `docs/architecture.html` into a polished, accurate high-level architecture reference for the Smart Mobility platform. The page should help a reader understand the whole system, the main service boundaries, and the important data flows without reading every service HLD first.

## Source Of Truth

The implementation should use the project code and configuration as the primary source of truth, then use existing HLD docs for context. When docs and code disagree, the page should prefer current code/config and call out the mismatch in a small notes section.

Primary sources inspected:

- Root `README.md`
- `docs/HLD/*.md`
- `docker/docker-compose.yml`
- Service `application.properties`
- Service controllers, Kafka consumers, Kafka producers, and client classes

## Deliverable

Create a browser-viewable architecture page at `docs/architecture.html`.

The page should be a single standalone HTML file with embedded CSS and JavaScript only. It should not require a dev server, build step, CDN dependency, or external font request.

## Page Structure

The page should include:

1. Hero/header with platform name, short system description, and key technology badges.
2. System HLD overview showing clients, API Gateway, services, Kafka, Redis, PostgreSQL, and Eureka.
3. Service inventory with responsibility, port, storage, and communication mode.
4. Data-flow sections:
   - Ride request and dispatch flow
   - Driver assignment response flow
   - Driver location and realtime fanout flow
   - Auth, user creation, and driver onboarding flow
5. Event and storage map covering Kafka topics, PostgreSQL databases, Redis keys/uses, and WebSocket destinations.
6. Accuracy notes for important code-vs-doc differences.

## Visual Design

The page should feel like an architecture dashboard, not a marketing landing page. It should use:

- A restrained dark technical theme with clear contrast.
- Full-width sections rather than cards nested inside cards.
- Service nodes grouped by layer: clients, edge, domain services, event/realtime, data stores, discovery.
- Distinct visual treatments for synchronous REST, asynchronous Kafka, WebSocket/STOMP, and storage/data access flows.
- Compact legends and labels so the diagrams are readable on desktop and still usable on mobile.

## Architecture Facts To Represent

Services:

- API Gateway: routes external HTTP traffic, JWT checks, internal API guard, rate limiting.
- Auth Service: registration, login, refresh token lifecycle, JWT issuance.
- User Service: user identity persistence and `user.created` publishing.
- Driver Service: driver profile creation; consumes `user.created`.
- Cab Service: ride lifecycle, dispatch API facade, produces `ride-requested`, `assignment-accepted`, and `assignment-rejected`; consumes `driver-assigned` and `matchmaking-failed`.
- Location Service: driver online/offline/update APIs; Redis GEO/SET-backed nearby driver lookup.
- Matchmaking Service: consumes `ride-requested` and driver responses, queries Location Service, reserves drivers in Redis, persists dispatch sessions, emits assignment outcomes.
- Realtime Gateway: consumes `driver-location-events` and `assignment-requested`, broadcasts to `/topic/trip/{rideId}` and `/topic/driver/{driverUserId}`.
- Eureka Service: service discovery.

Infrastructure:

- PostgreSQL with separate logical databases for auth, user, cab, driver, and matchmaking.
- Redis for rate limiting, location GEO indexes, driver availability, and matchmaking reservation/cache state.
- Kafka for asynchronous integration.

Current code/config details:

- Current defaults use ports 8080 gateway, 8091 auth, 8081 user, 8089 cab, 8084 driver, 8090 location, 8087 matchmaking, 8095 realtime, and 8761 Eureka.
- Gateway routes Cab through `/cab/**`, `/rides/**`, and `/dispatch/**`.
- Gateway routes Driver through `/driver/**` and `/drivers/**`.
- Gateway routes internal nearby lookup through guarded `/location/internal/nearby`.
- Matchmaking calls Location Service at `/internal/nearby` because its base URL points at the Location Service.
- Gateway exposes public driver location APIs and treats nearby/internal location lookup as internal-only.

## Behavior

The page should work by opening the file directly in a browser. Lightweight interactivity is allowed for filtering/highlighting flow types, but the core diagrams must be readable without interaction.

## Testing And Verification

After implementation, verify by opening `docs/architecture.html` in the browser or using a local browser automation check. Confirm:

- The page loads without network dependencies.
- No major text overlaps at desktop and mobile widths.
- Flow legends match the visual links.
- Service and topic names match the code/config.
- The old page content has been replaced with the new project-wide HLD and data-flow view.
