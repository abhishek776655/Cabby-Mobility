# Matchmaking Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `matchmaking-service` and the minimum cab/location/driver contracts needed for real nearby-driver assignment.

**Architecture:** Cab-service emits `ride-requested` with pickup/drop coordinates. Matchmaking consumes that event, asks location-service for nearby online driver IDs, checks driver-service profiles/availability, scores candidates by rating with location order as tie-breaker, marks the selected driver unavailable, stores audit rows, and emits `driver-assigned` or `matchmaking-failed`.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Spring WebMVC, Spring Kafka, Spring Data JPA, PostgreSQL, H2 tests, Lombok.

---

## File Map

- Modify cab-service ride contract: `cab-service/src/main/java/com/smartmobility/cab/dto/RideRequestDTO.java`, `RideResponseDTO.java`, `RideEntity.java`, `RideRequestedEvent.java`, `DriverAssignedEvent.java`, `RideMapper.java`, `RideService.java`, `RideServiceImpl.java`, ride state classes.
- Complete location-service API/service/errors: `location-service/src/main/java/com/smartmobility/location_service/controller/LocationController.java`, new `dto/ApiResponse*.java`, new `service/*`, new `exception/*`, `application.properties`, service tests.
- Add `matchmaking-service/` as a new Spring Boot service with `client`, `config`, `dto`, `entity`, `event`, `exception`, `kafka`, `repository`, `scoring`, and `service` packages.
- Modify infra config: `docker/init.sql`, `gateway-service/src/main/resources/application.properties` only if exposing matchmaking externally; otherwise leave gateway unchanged.
- Fix driver-service port to `8084` in `driver-service/src/main/resources/application.properties`.

---

### Task 1: Complete Location-Service Contract

**Files:**
- Modify: `location-service/src/main/java/com/smartmobility/location_service/controller/LocationController.java`
- Create: `location-service/src/main/java/com/smartmobility/location_service/dto/ApiResponse.java`
- Create: `location-service/src/main/java/com/smartmobility/location_service/dto/ApiResponseBuilder.java`
- Create: `location-service/src/main/java/com/smartmobility/location_service/service/LocationService.java`
- Create: `location-service/src/main/java/com/smartmobility/location_service/service/impl/LocationServiceImpl.java`
- Create: `location-service/src/main/java/com/smartmobility/location_service/exception/ErrorCodes.java`
- Create: `location-service/src/main/java/com/smartmobility/location_service/exception/InvalidLocationException.java`
- Create: `location-service/src/main/java/com/smartmobility/location_service/exception/LocationServiceException.java`
- Create: `location-service/src/main/java/com/smartmobility/location_service/exception/GlobalExceptionHandler.java`
- Modify: `location-service/src/main/resources/application.properties`
- Test: `location-service/src/test/java/com/smartmobility/location_service/LocationServiceImplTest.java`

- [ ] **Step 1: Write service tests with fake repository**

Use a fake `LocationRepository`, not Mockito, because local JDK may fail Mockito inline self-attach.

```java
@Test
void goOnlineStoresLocationAndMarksDriverOnline() {
    FakeLocationRepository repository = new FakeLocationRepository();
    LocationServiceImpl service = new LocationServiceImpl(repository);

    service.goOnline("42", 28.7041, 77.1025);

    assertEquals("42", repository.lastUpsertDriverId);
    assertEquals(28.7041, repository.lastLat);
    assertEquals(77.1025, repository.lastLng);
    assertEquals("42", repository.lastOnlineDriverId);
}

@Test
void getNearbyDriversRejectsInvalidRadius() {
    LocationServiceImpl service = new LocationServiceImpl(new FakeLocationRepository());

    assertThrows(
            InvalidLocationException.class,
            () -> service.getNearbyDrivers(28.7041, 77.1025, 100.0, 10)
    );
}

@Test
void getNearbyDriversReturnsRepositoryResults() {
    FakeLocationRepository repository = new FakeLocationRepository();
    repository.nearbyDrivers = List.of("42", "43");
    LocationServiceImpl service = new LocationServiceImpl(repository);

    assertEquals(
            List.of("42", "43"),
            service.getNearbyDrivers(28.7041, 77.1025, 5.0, 10)
    );
}
```

- [ ] **Step 2: Run red**

Run:

```bash
cd location-service
./mvnw test -q
```

Expected before implementation: compilation fails for missing `LocationService`, `ApiResponse`, or wrong controller imports.

- [ ] **Step 3: Implement location service layer and errors**

Implement `LocationServiceImpl` with:

```java
private static final double MAX_RADIUS_KM = 50.0;
private static final int MAX_LIMIT = 50;

public void goOnline(String driverId, double lat, double lng) {
    validateDriverId(driverId);
    validateCoordinates(lat, lng);
    try {
        locationRepository.upsertDriverLocation(driverId, lat, lng);
        locationRepository.markDriverOnline(driverId);
    } catch (RuntimeException ex) {
        throw new LocationServiceException("Failed to mark driver online", ex);
    }
}

public List<String> getNearbyDrivers(double lat, double lng, double radiusKm, int limit) {
    validateCoordinates(lat, lng);
    validateSearch(radiusKm, limit);
    try {
        return locationRepository.findNearbyDrivers(lat, lng, radiusKm, limit);
    } catch (RuntimeException ex) {
        throw new LocationServiceException("Failed to find nearby drivers", ex);
    }
}
```

Fix controller imports to `com.smartmobility.location_service.*` and return `ApiResponseBuilder.success(...)`.

- [ ] **Step 4: Add location-service config**

Set:

```properties
spring.application.name=location-service
server.port=8086
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

- [ ] **Step 5: Run green**

Run:

```bash
cd location-service
./mvnw test -q
```

Expected: pass.

---

### Task 2: Normalize Cab-Service Ride Event Contract

**Files:**
- Modify: `cab-service/src/main/java/com/smartmobility/cab/dto/RideRequestDTO.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/dto/RideResponseDTO.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/entity/RideEntity.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/event/RideRequestedEvent.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/event/DriverAssignedEvent.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/mapper/RideMapper.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/service/RideService.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/service/impl/RideServiceImpl.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/state/RideState.java`
- Modify: `cab-service/src/main/java/com/smartmobility/cab/state/impl/*.java`

- [ ] **Step 1: Add coordinate fields**

Add to `RideRequestDTO`, `RideResponseDTO`, `RideEntity`, and `RideRequestedEvent`:

```java
private Double pickupLatitude;
private Double pickupLongitude;
private Double dropLatitude;
private Double dropLongitude;
```

Use `@NotNull` on request fields and `@Column(nullable = false)` on entity fields.

- [ ] **Step 2: Align assigned driver ID type**

Change cab assigned `driverId` from `UUID` to `Long` everywhere:

```java
private Long driverId;
void assignDriver(RideEntity ride, Long driverId);
void handleDriverAssignedEvent(String eventId, UUID rideId, Long driverId);
```

Reason: driver-service exposes driver identity as `userId: Long`.

- [ ] **Step 3: Map coordinates and initial status**

In `RideMapper.toEntity`, set:

```java
.pickupLatitude(request.getPickupLatitude())
.pickupLongitude(request.getPickupLongitude())
.dropLatitude(request.getDropLatitude())
.dropLongitude(request.getDropLongitude())
.status(RideStatus.MATCHING)
```

`MATCHING` is required because `MatchingState.assignDriver(...)` is the legal transition for `driver-assigned`.

- [ ] **Step 4: Emit coordinates in `ride-requested`**

In `RideServiceImpl.createRide`, add:

```java
.pickupLatitude(savedRide.getPickupLatitude())
.pickupLongitude(savedRide.getPickupLongitude())
.dropLatitude(savedRide.getDropLatitude())
.dropLongitude(savedRide.getDropLongitude())
```

- [ ] **Step 5: Run cab tests**

Run:

```bash
cd cab-service
./mvnw test -q
```

Expected: compile succeeds and context test passes. If Kafka listener logs connection warnings under sandbox but exits 0, accept for now.

---

### Task 3: Scaffold Matchmaking Service

**Files:**
- Create: `matchmaking-service/pom.xml`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/MatchmakingServiceApplication.java`
- Create: `matchmaking-service/src/main/resources/application.properties`
- Create: `matchmaking-service/src/test/resources/application.properties`
- Create: `matchmaking-service/src/test/java/com/smartmobility/matchmaking/MatchmakingServiceApplicationTests.java`

- [ ] **Step 1: Create Spring Boot app**

Create:

```java
package com.smartmobility.matchmaking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MatchmakingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatchmakingServiceApplication.class, args);
    }
}
```

- [ ] **Step 2: Create `pom.xml`**

Use Spring Boot `4.0.5`, Java 21, and dependencies:

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-kafka</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
<dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
```

- [ ] **Step 3: Add properties**

Production:

```properties
spring.application.name=matchmaking-service
server.port=8087
spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${MATCHMAKING_DB}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.kafka.bootstrap-servers=localhost:9092
services.location.url=http://localhost:8086
services.driver.url=http://localhost:8084
matchmaking.default-radius-km=5
matchmaking.default-limit=10
```

Test:

```properties
spring.datasource.url=jdbc:h2:mem:matchmakingdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.kafka.listener.auto-startup=false
services.location.url=http://localhost:18086
services.driver.url=http://localhost:18084
matchmaking.default-radius-km=5
matchmaking.default-limit=10
```

- [ ] **Step 4: Run context test**

Run:

```bash
cd matchmaking-service
./mvnw test -q
```

Expected after app skeleton: pass.

---

### Task 4: Add Matchmaking Domain And Repositories

**Files:**
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/entity/AssignmentAttempt.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/entity/AssignmentStatus.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/entity/ProcessedEvent.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/repository/AssignmentAttemptRepository.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/repository/ProcessedEventRepository.java`

- [ ] **Step 1: Implement entities**

Use:

```java
public enum AssignmentStatus {
    CONSIDERED,
    ASSIGNED,
    FAILED
}
```

`AssignmentAttempt` fields:

```java
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
@Column(nullable = false)
private UUID rideId;
private Long driverId;
private Double score;
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private AssignmentStatus status;
private String failureReason;
@Column(nullable = false, updatable = false)
private LocalDateTime createdAt;
@PrePersist void prePersist() { this.createdAt = LocalDateTime.now(); }
```

`ProcessedEvent` fields:

```java
@Id
private String eventId;
@Column(nullable = false)
private String eventType;
@Column(nullable = false)
private LocalDateTime processedAt;
```

- [ ] **Step 2: Implement repositories**

```java
public interface AssignmentAttemptRepository extends JpaRepository<AssignmentAttempt, Long> {
    List<AssignmentAttempt> findByRideId(UUID rideId);
}

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
```

- [ ] **Step 3: Run tests**

Run:

```bash
cd matchmaking-service
./mvnw test -q
```

Expected: pass.

---

### Task 5: Add Events, DTOs, Clients, And Scoring

**Files:**
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/event/RideRequestedEvent.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/event/DriverAssignedEvent.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/event/MatchmakingFailedEvent.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/dto/ApiResponse.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/dto/NearbyDriversRequest.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/dto/DriverResponseDTO.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/client/LocationServiceClient.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/client/DriverServiceClient.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/scoring/DriverScoringStrategy.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/scoring/RatingDriverScoringStrategy.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/config/RestClientConfig.java`

- [ ] **Step 1: Define event classes**

`RideRequestedEvent`:

```java
private String eventId;
private UUID rideId;
private UUID riderId;
private String pickupLocation;
private String dropLocation;
private Double pickupLatitude;
private Double pickupLongitude;
private Double dropLatitude;
private Double dropLongitude;
```

`DriverAssignedEvent`:

```java
private String eventId;
private UUID rideId;
private Long driverId;
private LocalDateTime assignedAt;
```

`MatchmakingFailedEvent`:

```java
private String eventId;
private UUID rideId;
private String reason;
private LocalDateTime failedAt;
```

- [ ] **Step 2: Define REST DTOs**

`ApiResponse<T>` matches existing service response shape:

```java
private boolean success;
private T data;
private String message;
private LocalDateTime timestamp;
private int status;
private String error;
```

`DriverResponseDTO`:

```java
private Long userId;
private Double rating;
private Boolean available;
private String vehicleDetails;
```

- [ ] **Step 3: Implement REST clients**

Use Spring `RestClient`.

`LocationServiceClient.findNearbyDrivers(...)` posts to `/location/nearby` and returns `List<Long>` by parsing returned string IDs:

```java
return response.getData().stream()
        .map(Long::valueOf)
        .toList();
```

`DriverServiceClient.getDriver(Long userId)` calls `GET /drivers/{userId}`.

`DriverServiceClient.markUnavailable(Long userId)` calls:

```java
PATCH /drivers/{userId}/availability?available=false
```

- [ ] **Step 4: Implement scoring**

```java
public interface DriverScoringStrategy {
    double calculateScore(DriverResponseDTO driver, RideRequestedEvent ride);
}

@Component
public class RatingDriverScoringStrategy implements DriverScoringStrategy {
    public double calculateScore(DriverResponseDTO driver, RideRequestedEvent ride) {
        return driver.getRating() == null ? 0.0 : driver.getRating();
    }
}
```

- [ ] **Step 5: Run compile**

Run:

```bash
cd matchmaking-service
./mvnw test -q
```

Expected: pass.

---

### Task 6: Implement Core Matchmaking Service With Tests

**Files:**
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/service/MatchmakingService.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/service/impl/MatchmakingServiceImpl.java`
- Create: `matchmaking-service/src/test/java/com/smartmobility/matchmaking/MatchmakingServiceImplTest.java`

- [ ] **Step 1: Write tests with fakes**

Avoid Mockito; use fake client/repository/producer classes.

Test cases:

```java
@Test
void selectsHighestRatedAvailableDriver() {
    FakeLocationServiceClient locationClient = new FakeLocationServiceClient(List.of(101L, 102L));
    FakeDriverServiceClient driverClient = new FakeDriverServiceClient();
    driverClient.drivers.put(101L, driver(101L, 4.7, true));
    driverClient.drivers.put(102L, driver(102L, 4.9, true));
    FakeMatchmakingEventProducer producer = new FakeMatchmakingEventProducer();
    MatchmakingServiceImpl service = service(locationClient, driverClient, producer);

    service.matchRide(rideRequestedEvent("evt-1"));

    assertEquals(102L, producer.assignedEvent.getDriverId());
    assertTrue(driverClient.unavailableDrivers.contains(102L));
}

@Test
void keepsLocationOrderWhenRatingsTie() {
    FakeLocationServiceClient locationClient = new FakeLocationServiceClient(List.of(101L, 102L));
    FakeDriverServiceClient driverClient = new FakeDriverServiceClient();
    driverClient.drivers.put(101L, driver(101L, 5.0, true));
    driverClient.drivers.put(102L, driver(102L, 5.0, true));
    FakeMatchmakingEventProducer producer = new FakeMatchmakingEventProducer();
    MatchmakingServiceImpl service = service(locationClient, driverClient, producer);

    service.matchRide(rideRequestedEvent("evt-1"));

    assertEquals(101L, producer.assignedEvent.getDriverId());
}

@Test
void publishesFailureWhenNoDriverAvailable() {
    FakeLocationServiceClient locationClient = new FakeLocationServiceClient(List.of(101L));
    FakeDriverServiceClient driverClient = new FakeDriverServiceClient();
    driverClient.drivers.put(101L, driver(101L, 5.0, false));
    FakeMatchmakingEventProducer producer = new FakeMatchmakingEventProducer();
    MatchmakingServiceImpl service = service(locationClient, driverClient, producer);

    service.matchRide(rideRequestedEvent("evt-1"));

    assertNull(producer.assignedEvent);
    assertEquals("NO_DRIVER_AVAILABLE", producer.failedEvent.getReason());
}

@Test
void ignoresDuplicateEvent() {
    FakeLocationServiceClient locationClient = new FakeLocationServiceClient(List.of(101L));
    FakeDriverServiceClient driverClient = new FakeDriverServiceClient();
    FakeMatchmakingEventProducer producer = new FakeMatchmakingEventProducer();
    MatchmakingServiceImpl service = service(locationClient, driverClient, producer);
    service.saveProcessedForTest("evt-1");

    service.matchRide(rideRequestedEvent("evt-1"));

    assertNull(producer.assignedEvent);
    assertNull(producer.failedEvent);
}
```

Support helpers:

```java
private RideRequestedEvent rideRequestedEvent(String eventId) {
    return RideRequestedEvent.builder()
            .eventId(eventId)
            .rideId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .riderId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
            .pickupLatitude(28.7041)
            .pickupLongitude(77.1025)
            .dropLatitude(28.5355)
            .dropLongitude(77.3910)
            .build();
}

private DriverResponseDTO driver(Long userId, Double rating, Boolean available) {
    return DriverResponseDTO.builder()
            .userId(userId)
            .rating(rating)
            .available(available)
            .build();
}
```

- [ ] **Step 2: Implement service algorithm**

Algorithm:

```java
public void matchRide(RideRequestedEvent event) {
    if (processedEventRepository.existsById(event.getEventId())) {
        return;
    }

    List<Long> candidateIds = locationClient.findNearbyDrivers(
            event.getPickupLatitude(),
            event.getPickupLongitude(),
            defaultRadiusKm,
            defaultLimit
    );

    DriverResponseDTO selectedDriver = null;
    double selectedScore = -1;

    for (Long candidateId : candidateIds) {
        DriverResponseDTO driver = driverClient.getDriver(candidateId);
        if (!Boolean.TRUE.equals(driver.getAvailable())) {
            saveAttempt(event.getRideId(), candidateId, 0.0, FAILED, "DRIVER_UNAVAILABLE");
            continue;
        }

        double score = scoringStrategy.calculateScore(driver, event);
        saveAttempt(event.getRideId(), candidateId, score, CONSIDERED, null);

        if (selectedDriver == null || score > selectedScore) {
            selectedDriver = driver;
            selectedScore = score;
        }
    }

    if (selectedDriver == null) {
        saveFailureAndPublish(event, "NO_DRIVER_AVAILABLE");
        saveProcessed(event);
        return;
    }

    assignOrTryNext(event, selectedDriver);
    saveProcessed(event);
}
```

If `markUnavailable` fails for selected driver, mark that attempt failed with `DRIVER_RESERVATION_FAILED` and try next eligible candidate from ranked list.

- [ ] **Step 3: Run unit tests**

Run:

```bash
cd matchmaking-service
./mvnw test -q
```

Expected: pass.

---

### Task 7: Add Kafka Consumer, Producer, And Error Handling

**Files:**
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/kafka/RideRequestedConsumer.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/kafka/MatchmakingEventProducer.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/config/KafkaConsumerConfig.java`
- Create: `matchmaking-service/src/main/java/com/smartmobility/matchmaking/config/KafkaErrorHandlerConfig.java`

- [ ] **Step 1: Implement producer**

Topics:

```java
private static final String DRIVER_ASSIGNED_TOPIC = "driver-assigned";
private static final String MATCHMAKING_FAILED_TOPIC = "matchmaking-failed";
```

Producer methods:

```java
void publishDriverAssigned(DriverAssignedEvent event) {
    kafkaTemplate.send(DRIVER_ASSIGNED_TOPIC, event.getRideId().toString(), event);
}

void publishMatchmakingFailed(MatchmakingFailedEvent event) {
    kafkaTemplate.send(MATCHMAKING_FAILED_TOPIC, event.getRideId().toString(), event);
}
```

- [ ] **Step 2: Implement consumer**

```java
@KafkaListener(
    topics = "ride-requested",
    groupId = "matchmaking-service-group",
    containerFactory = "kafkaListenerContainerFactory"
)
public void consume(RideRequestedEvent event) {
    matchmakingService.matchRide(event);
}
```

- [ ] **Step 3: Implement error handler**

Use same pattern as cab/driver:

```java
DeadLetterPublishingRecoverer recoverer =
    new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
    );

DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));
```

- [ ] **Step 4: Run tests**

Run:

```bash
cd matchmaking-service
./mvnw test -q
```

Expected: pass with Kafka listener auto-start disabled in tests.

---

### Task 8: Update Infra And Port Config

**Files:**
- Modify: `driver-service/src/main/resources/application.properties`
- Modify: `docker/init.sql`
- Optional modify: `gateway-service/src/main/resources/application.properties`

- [ ] **Step 1: Move driver-service port**

Set:

```properties
server.port=8084
```

- [ ] **Step 2: Add matchmaking DB**

Add to `docker/init.sql`:

```sql
CREATE DATABASE matchmaking_db;
```

- [ ] **Step 3: Gateway route only if external access wanted**

If exposing matchmaking internal APIs through gateway, add route:

```properties
spring.cloud.gateway.routes[3].id=matchmaking-service
spring.cloud.gateway.routes[3].uri=http://localhost:8087
spring.cloud.gateway.routes[3].predicates[0]=Path=/matchmaking/**
```

If no public/internal API is added to matchmaking v1, skip gateway route.

- [ ] **Step 4: Run changed service tests**

Run:

```bash
cd driver-service && ./mvnw test -q
cd ../location-service && ./mvnw test -q
cd ../cab-service && ./mvnw test -q
cd ../matchmaking-service && ./mvnw test -q
```

Expected: all pass. If driver-service fails due missing test H2 config, add `driver-service/src/test/resources/application.properties` mirroring other services' H2 test config and rerun.

---

### Task 9: Final Verification

**Files:**
- No new code unless prior test failures identify focused fixes.

- [ ] **Step 1: Check compile across changed services**

Run each service test command from Task 8.

- [ ] **Step 2: Inspect git diff**

Run:

```bash
git diff -- cab-service location-service driver-service matchmaking-service docker/init.sql
```

Expected: only planned changes appear.

- [ ] **Step 3: Commit implementation in focused commits**

Suggested commits:

```bash
git add location-service
git commit -m "feat: complete location service API"

git add cab-service
git commit -m "feat: add ride coordinates for matchmaking"

git add matchmaking-service docker/init.sql driver-service/src/main/resources/application.properties
git commit -m "feat: add matchmaking service"
```
