# Location Service Geo Improvement - Design Document

## 1. Problem Statement

The current location service has an N+1 performance issue when finding nearby drivers:

```
1. GEOSEARCH drivers:geo → returns N drivers (sorted by distance)
2. For EACH driver: isMember(drivers:available, driverId) → N additional Redis calls
3. Total: N+1 Redis calls
```

If search returns 50 drivers, we make 51 Redis calls just to filter online drivers.

## 2. Additional Issues

- **Race condition**: Driver could go offline between search and filter
- **Sorting broken**: Removing filtered drivers from middle breaks distance ordering
- **Code complexity**: Requires stream + filter with helper method

## 3. Solution: Separate GEO for Available Drivers

### 3.1 Two GEO Keys

| Key | Contents | Purpose |
|-----|----------|---------|
| `drivers:geo` | ALL drivers | History/analytics (not deleted on offline) |
| `drivers:available:geo` | ONLY online drivers | Active dispatch lookup |

### 3.2 Data Flow

**goOnline(driverId, lat, lng):**
```
GEOADD drivers:geo {lng} {lat} driverId
GEOADD drivers:available:geo {lng} {lat} driverId
```

**goOffline(driverId):**
```
ZREM drivers:available:geo driverId
(driver stays in drivers:geo for history)
```

**updateLocation(driverId, lat, lng):**
```
GEOADD drivers:geo {lng} {lat} driverId
GEOADD drivers:available:geo {lng} {lat} driverId  (if still online - handled by separate online check or assume online on update)
```

**getNearbyDrivers(lat, lng, radius, limit):**
```
GEOSEARCH drivers:available:geo ... → returns ONLY online drivers
(No filtering needed!)
```

## 4. Benefits

| Metric | Before | After |
|--------|--------|-------|
| Redis calls | N+1 | 1 |
| Sorting | Broken after filter | Preserved |
| Atomicity | Race condition | Atomic |
| Code complexity | Stream + filter + helper | Simple GEOSEARCH |

## 5. Files to Modify

### 5.1 RedisKeys.java
Add new key constant:
```java
public static final String DRIVERS_AVAILABLE_GEO = "drivers:available:geo";
```

### 5.2 LocationRepositoryImpl.java
Update 4 methods:
- `upsertDriverLocation()` - add to both keys
- `markDriverOnline()` - also add to available geo
- `markDriverOffline()` - remove from available geo only
- `findNearbyDrivers()` - search available geo only (remove filter)

### 5.3 No Changes Needed
- `LocationServiceImpl.java` - delegates to repository
- Controller and DTOs remain unchanged

## 6. Migration Considerations

- Existing data in `drivers:geo` remains untouched
- New drivers going online automatically added to both keys
- Drivers going offline: removed from available only, kept in main geo for history

## 7. Backward Compatibility

- API endpoints unchanged
- Same input/output contract for `getNearbyDrivers`
- Only internal implementation changes