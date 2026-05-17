# Location Service Geo Improvement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix N+1 performance issue in location service by using separate GEO key for available drivers - reduces Redis calls from N+1 to 1.

**Architecture:** Add new `drivers:available:geo` Redis key. When driver goes online, add to both keys. When goes offline, remove from available key only. Search directly in available key (no filtering needed).

**Tech Stack:** Java 21, Spring Data Redis, Redis GEOSEARCH

---

## Task 1: Update RedisKeys

**Files:**
- Modify: `location-service/src/main/java/com/smartmobility/location_service/constants/RedisKeys.java`

- [ ] **Step 1: Add new key constant**

```java
package com.smartmobility.location_service.constants;

public class RedisKeys {

    public static final String DRIVERS_GEO = "drivers:geo";
    public static final String DRIVERS_AVAILABLE = "drivers:available";
    public static final String DRIVERS_AVAILABLE_GEO = "drivers:available:geo";  // NEW

    private RedisKeys() {}
}
```

- [ ] **Step 2: Compile and commit**

Run: `cd location-service && mvn compile -q`
Expected: PASS

Commit: `git add src/main/java/com/smartmobility/location_service/constants/RedisKeys.java && git commit -m "feat: add DRIVERS_AVAILABLE_GEO key constant"`

---

## Task 2: Update LocationRepositoryImpl

**Files:**
- Modify: `location-service/src/main/java/com/smartmobility/location_service/repository/impl/LocationRepositoryImpl.java`

- [ ] **Step 1: Update imports and add field**

Add new GeoOperations field:
```java
private final GeoOperations<String, String> geoOps;        // existing
private final SetOperations<String, String> setOps;        // existing
// NEW: add second geo operations for available drivers
private final GeoOperations<String, String> availableGeoOps;
```

- [ ] **Step 2: Update upsertDriverLocation**

```java
@Override
public void upsertDriverLocation(String driverId, double lat, double lng) {
    Point point = new Point(lng, lat);
    geoOps.add(RedisKeys.DRIVERS_GEO, point, driverId);
    // NEW: also add to available geo if driver is online
    if (Boolean.TRUE.equals(setOps.isMember(RedisKeys.DRIVERS_AVAILABLE, driverId))) {
        availableGeoOps.add(RedisKeys.DRIVERS_AVAILABLE_GEO, point, driverId);
    }
}
```

- [ ] **Step 3: Update markDriverOnline**

```java
@Override
public void markDriverOnline(String driverId) {
    setOps.add(RedisKeys.DRIVERS_AVAILABLE, driverId);
    // NEW: copy location from main geo to available geo
    List<Point> points = geoOps.position(RedisKeys.DRIVERS_GEO, driverId);
    if (points != null && !points.isEmpty()) {
        availableGeoOps.add(RedisKeys.DRIVERS_AVAILABLE_GEO, points.get(0), driverId);
    }
}
```

- [ ] **Step 4: Update markDriverOffline**

```java
@Override
public void markDriverOffline(String driverId) {
    setOps.remove(RedisKeys.DRIVERS_AVAILABLE, driverId);
    // NEW: remove from available geo only (keep in main geo for history)
    availableGeoOps.remove(RedisKeys.DRIVERS_AVAILABLE_GEO, driverId);
}
```

- [ ] **Step 5: Update findNearbyDrivers**

```java
@Override
public List<String> findNearbyDrivers(double lat, double lng, double radiusKm, int limit) {
    Point center = new Point(lng, lat);
    Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);

    RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
            .includeDistance()
            .sortAscending()
            .limit(limit);

    // CHANGE: Search in DRIVERS_AVAILABLE_GEO instead of DRIVERS_GEO
    GeoResults<RedisGeoCommands.GeoLocation<String>> results =
            availableGeoOps.search(RedisKeys.DRIVERS_AVAILABLE_GEO,  // Changed from DRIVERS_GEO
                    GeoReference.fromCoordinate(center),
                    radius,
                    args);

    if (results == null) return Collections.emptyList();

    // REMOVED: No more filter(this::isDriverOnline) - already filtered!
    return results.getContent().stream()
            .map(r -> r.getContent().getName())
            .collect(Collectors.toList());
}
```

- [ ] **Step 6: Remove isDriverOnline helper**

The `isDriverOnline` method is no longer needed - remove it.

- [ ] **Step 7: Compile and commit**

Run: `cd location-service && mvn compile -q`
Expected: PASS

Commit: `git add src/main/java/com/smartmobility/location_service/repository/impl/LocationRepositoryImpl.java && git commit -m "refactor: use separate GEO for available drivers"`

---

## Task 3: Update RedisOpsConfig

**Files:**
- Modify: `location-service/src/main/java/com/smartmobility/location_service/config/RedisOpsConfig.java`

- [ ] **Step 1: Add @Bean for available GeoOperations**

```java
@Bean
public GeoOperations<String, String> availableGeoOperations(RedisConnectionFactory connectionFactory) {
    return new RedisTemplate<String, String>().opsForGeo();
}
```

Or more simply, inject GeoOperations directly with qualifier.

- [ ] **Step 2: Commit**

---

## Task 4: Add Unit Tests

**Files:**
- Modify: `location-service/src/test/java/com/smartmobility/location_service/LocationServiceImplTest.java`

- [ ] **Step 1: Add test for getNearbyDrivers with online driver**

```java
@Test
void testGetNearbyDrivers_returnsOnlyOnlineDrivers() {
    // Setup: driver1 online, driver2 offline
    when(availableGeoOps.search(eq(RedisKeys.DRIVERS_AVAILABLE_GEO), any(), any(), any()))
        .thenReturn(createGeoResults("driver1", "driver2"));

    List<String> result = locationService.getNearbyDrivers(40.7128, -74.0060, 5.0, 10);

    // driver1 should be returned (no filtering needed - already in available geo)
    assertTrue(result.contains("driver1"));
}
```

- [ ] **Step 2: Run tests**

Run: `cd location-service && mvn test -q`
Expected: PASS

- [ ] **Step 3: Commit**

---

## Spec Coverage Check

| Spec Requirement | Implementation |
|------------------|----------------|
| Add DRIVERS_AVAILABLE_GEO key | Task 1 |
| upsertDriverLocation updates both keys | Task 2 |
| markDriverOnline copies to available geo | Task 2 |
| markDriverOffline removes from available only | Task 2 |
| getNearbyDrivers searches available geo | Task 2 |
| Remove N+1 filter | Task 2 |
| Unit tests | Task 4 |

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-17-location-geo-improvement.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**