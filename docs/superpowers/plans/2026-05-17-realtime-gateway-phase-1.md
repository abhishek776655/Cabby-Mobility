# Realtime Gateway Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a stateless Spring Boot 3 Realtime Gateway that consumes Kafka driver location and assignment events, then broadcasts them to rider and driver WebSocket/STOMP topics.

**Architecture:** The service is a Kafka-to-STOMP fanout layer. Location Service owns driver GPS ingestion and publishes `driver-location-events`; Matchmaking owns assignment decisions and publishes `assignment-requested`; Realtime Gateway only consumes, validates routing fields, and broadcasts to `/topic/trip/{rideId}` or `/topic/driver/{driverId}`.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring WebSocket, STOMP SimpleBroker, Spring Kafka, Maven, Lombok, JUnit 5, Mockito.

---

## File Map

- Modify: `realtime-gateway-service/pom.xml` - pin Spring Boot 3, dependencies, compiler config.
- Delete: `realtime-gateway-service/src/main/resources/application.properties` - replaced by YAML.
- Create: `realtime-gateway-service/src/main/resources/application.yml` - server, Kafka, actuator, realtime topics, WebSocket settings.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/config/WebSocketConfig.java` - STOMP endpoint and broker config.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/config/KafkaConsumerConfig.java` - typed Kafka consumer factories.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/config/KafkaErrorHandlerConfig.java` - retry/backoff logging.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/domain/RealtimeDestinations.java` - topic destination helpers.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/dto/DriverLocationUpdatedEvent.java` - location event DTO.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/dto/AssignmentRequestedEvent.java` - assignment event DTO.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/exception/InvalidRealtimeEventException.java` - invalid routing payload exception.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/service/RealtimeBroadcastService.java` - WebSocket fanout service.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/kafka/DriverLocationConsumer.java` - consume location events.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/kafka/DriverAssignmentConsumer.java` - consume assignment events.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/handler/WebSocketEventListener.java` - session connect/subscribe/disconnect logging.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/controller/RealtimeInfoController.java` - lightweight service info endpoint.
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/util/StringSanitizer.java` - small blank/string guard utility.
- Create: `realtime-gateway-service/src/main/resources/static/ws-test.html` - browser STOMP test client.
- Create: `realtime-gateway-service/README.md` - local run and end-to-end test guide.
- Modify: `docker/docker-compose.yml` - move Kafka UI host port from `8085` to `8090`.
- Modify: `realtime-gateway-service/src/test/java/com/mobility/realtime/RealtimeGatewayServiceApplicationTests.java` - context test with Kafka auto-start disabled.
- Create: `realtime-gateway-service/src/test/java/com/mobility/realtime/service/RealtimeBroadcastServiceTest.java` - destination tests.
- Create: `realtime-gateway-service/src/test/java/com/mobility/realtime/kafka/DriverLocationConsumerTest.java` - consumer delegation/validation tests.
- Create: `realtime-gateway-service/src/test/java/com/mobility/realtime/kafka/DriverAssignmentConsumerTest.java` - consumer delegation/validation tests.

---

## Task 1: Maven And Application Configuration

**Files:**
- Modify: `realtime-gateway-service/pom.xml`
- Delete: `realtime-gateway-service/src/main/resources/application.properties`
- Create: `realtime-gateway-service/src/main/resources/application.yml`
- Modify: `realtime-gateway-service/src/test/java/com/mobility/realtime/RealtimeGatewayServiceApplicationTests.java`

- [ ] **Step 1: Replace `pom.xml` with Spring Boot 3 dependencies**

Use this complete `realtime-gateway-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.mobility</groupId>
    <artifactId>realtime-gateway-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>realtime-gateway-service</name>
    <description>Realtime Kafka to WebSocket gateway for Smart Mobility</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Replace properties with YAML configuration**

Delete `realtime-gateway-service/src/main/resources/application.properties`.

Create `realtime-gateway-service/src/main/resources/application.yml`:

```yaml
server:
  port: 8085

spring:
  application:
    name: realtime-gateway-service
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: realtime-gateway-service-group
      auto-offset-reset: latest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: com.mobility.realtime.dto

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

realtime:
  kafka:
    topics:
      driver-location-events: driver-location-events
      assignment-requested-events: assignment-requested
  websocket:
    endpoint: /ws
    application-prefix: /app
    topic-prefix: /topic
```

- [ ] **Step 3: Disable Kafka listener startup in context test**

Replace `realtime-gateway-service/src/test/java/com/mobility/realtime/RealtimeGatewayServiceApplicationTests.java`:

```java
package com.mobility.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class RealtimeGatewayServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Run the service tests**

Run:

```bash
cd realtime-gateway-service
./mvnw test
```

Expected: tests compile. This task may fail until later tasks add referenced beans; after Task 5 it must pass.

- [ ] **Step 5: Commit**

```bash
git add realtime-gateway-service/pom.xml realtime-gateway-service/src/main/resources/application.yml realtime-gateway-service/src/test/java/com/mobility/realtime/RealtimeGatewayServiceApplicationTests.java
git add -u realtime-gateway-service/src/main/resources/application.properties
git commit -m "chore: configure realtime gateway service"
```

---

## Task 2: DTOs, Routing Constants, And Validation Exception

**Files:**
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/dto/DriverLocationUpdatedEvent.java`
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/dto/AssignmentRequestedEvent.java`
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/domain/RealtimeDestinations.java`
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/exception/InvalidRealtimeEventException.java`
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/util/StringSanitizer.java`

- [ ] **Step 1: Add DTO for driver location events**

Create `DriverLocationUpdatedEvent.java`:

```java
package com.mobility.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationUpdatedEvent {

    @NotBlank
    private String driverId;

    @NotBlank
    private String rideId;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    private Double speed;
    private Double heading;

    @NotNull
    private Instant timestamp;
}
```

- [ ] **Step 2: Add DTO for assignment requested events**

Create `AssignmentRequestedEvent.java`:

```java
package com.mobility.realtime.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRequestedEvent {

    private String eventId;

    @Builder.Default
    private String eventType = "ASSIGNMENT_REQUESTED";

    private UUID dispatchId;
    private UUID rideId;

    @NotNull
    private Long driverId;

    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupLocation;
    private Instant expiresAt;
}
```

- [ ] **Step 3: Add destination helper**

Create `RealtimeDestinations.java`:

```java
package com.mobility.realtime.domain;

import com.mobility.realtime.util.StringSanitizer;

public final class RealtimeDestinations {

    public static final String TOPIC_PREFIX = "/topic";
    public static final String TRIP_TOPIC_PREFIX = TOPIC_PREFIX + "/trip";
    public static final String DRIVER_TOPIC_PREFIX = TOPIC_PREFIX + "/driver";

    private RealtimeDestinations() {
    }

    public static String trip(String rideId) {
        return TRIP_TOPIC_PREFIX + "/" + StringSanitizer.requireText(rideId, "rideId");
    }

    public static String driver(Long driverId) {
        if (driverId == null) {
            throw new IllegalArgumentException("driverId must not be null");
        }
        return DRIVER_TOPIC_PREFIX + "/" + driverId;
    }
}
```

- [ ] **Step 4: Add string validation utility**

Create `StringSanitizer.java`:

```java
package com.mobility.realtime.util;

public final class StringSanitizer {

    private StringSanitizer() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
```

- [ ] **Step 5: Add invalid event exception**

Create `InvalidRealtimeEventException.java`:

```java
package com.mobility.realtime.exception;

public class InvalidRealtimeEventException extends RuntimeException {

    public InvalidRealtimeEventException(String message) {
        super(message);
    }
}
```

- [ ] **Step 6: Run tests**

Run:

```bash
cd realtime-gateway-service
./mvnw test
```

Expected: compile succeeds for the new DTO/domain classes.

- [ ] **Step 7: Commit**

```bash
git add realtime-gateway-service/src/main/java/com/mobility/realtime/dto realtime-gateway-service/src/main/java/com/mobility/realtime/domain realtime-gateway-service/src/main/java/com/mobility/realtime/exception realtime-gateway-service/src/main/java/com/mobility/realtime/util
git commit -m "feat: add realtime event contracts"
```

---

## Task 3: Broadcast Service With Unit Tests

**Files:**
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/service/RealtimeBroadcastService.java`
- Create: `realtime-gateway-service/src/test/java/com/mobility/realtime/service/RealtimeBroadcastServiceTest.java`

- [ ] **Step 1: Write broadcast service tests**

Create `RealtimeBroadcastServiceTest.java`:

```java
package com.mobility.realtime.service;

import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeBroadcastServiceTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RealtimeBroadcastService broadcastService = new RealtimeBroadcastService(messagingTemplate);

    @Test
    void broadcastDriverLocationSendsToTripTopic() {
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverId("driver_1")
                .rideId("ride_123")
                .latitude(28.6139)
                .longitude(77.2090)
                .speed(42.0)
                .heading(120.0)
                .timestamp(Instant.parse("2026-05-17T12:00:00Z"))
                .build();

        broadcastService.broadcastDriverLocation(event);

        verify(messagingTemplate).convertAndSend("/topic/trip/ride_123", event);
    }

    @Test
    void broadcastDriverLocationRejectsMissingRideId() {
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverId("driver_1")
                .latitude(28.6139)
                .longitude(77.2090)
                .timestamp(Instant.parse("2026-05-17T12:00:00Z"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> broadcastService.broadcastDriverLocation(event));
    }

    @Test
    void broadcastAssignmentRequestSendsToDriverTopic() {
        AssignmentRequestedEvent event = AssignmentRequestedEvent.builder()
                .driverId(1L)
                .pickupLatitude(28.6139)
                .pickupLongitude(77.2090)
                .pickupLocation("Connaught Place")
                .expiresAt(Instant.parse("2026-05-17T12:00:15Z"))
                .build();

        broadcastService.broadcastAssignmentRequest(event);

        verify(messagingTemplate).convertAndSend("/topic/driver/1", event);
    }

    @Test
    void broadcastAssignmentRequestRejectsMissingDriverId() {
        AssignmentRequestedEvent event = AssignmentRequestedEvent.builder().build();

        assertThrows(IllegalArgumentException.class, () -> broadcastService.broadcastAssignmentRequest(event));
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
cd realtime-gateway-service
./mvnw -Dtest=RealtimeBroadcastServiceTest test
```

Expected: FAIL because `RealtimeBroadcastService` does not exist.

- [ ] **Step 3: Add broadcast service implementation**

Create `RealtimeBroadcastService.java`:

```java
package com.mobility.realtime.service;

import com.mobility.realtime.domain.RealtimeDestinations;
import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastDriverLocation(DriverLocationUpdatedEvent event) {
        String destination = RealtimeDestinations.trip(event.getRideId());
        messagingTemplate.convertAndSend(destination, event);
        log.debug("Broadcasted driver location rideId={} driverId={} destination={}",
                event.getRideId(), event.getDriverId(), destination);
    }

    public void broadcastAssignmentRequest(AssignmentRequestedEvent event) {
        String destination = RealtimeDestinations.driver(event.getDriverId());
        messagingTemplate.convertAndSend(destination, event);
        log.info("Broadcasted assignment request driverId={} rideId={} dispatchId={} destination={}",
                event.getDriverId(), event.getRideId(), event.getDispatchId(), destination);
    }
}
```

- [ ] **Step 4: Run broadcast tests**

Run:

```bash
cd realtime-gateway-service
./mvnw -Dtest=RealtimeBroadcastServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add realtime-gateway-service/src/main/java/com/mobility/realtime/service/RealtimeBroadcastService.java realtime-gateway-service/src/test/java/com/mobility/realtime/service/RealtimeBroadcastServiceTest.java
git commit -m "feat: add realtime broadcast service"
```

---

## Task 4: WebSocket Configuration And Connection Event Logging

**Files:**
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/config/WebSocketConfig.java`
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/handler/WebSocketEventListener.java`

- [ ] **Step 1: Add WebSocket/STOMP configuration**

Create `WebSocketConfig.java`:

```java
package com.mobility.realtime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${realtime.websocket.endpoint:/ws}")
    private String endpoint;

    @Value("${realtime.websocket.application-prefix:/app}")
    private String applicationPrefix;

    @Value("${realtime.websocket.topic-prefix:/topic}")
    private String topicPrefix;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(topicPrefix);
        registry.setApplicationDestinationPrefixes(applicationPrefix);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns("*");
    }
}
```

- [ ] **Step 2: Add WebSocket session event listener**

Create `WebSocketEventListener.java`:

```java
package com.mobility.realtime.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Slf4j
@Component
public class WebSocketEventListener {

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket connected sessionId={}", accessor.getSessionId());
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket subscribed sessionId={} destination={}",
                accessor.getSessionId(), accessor.getDestination());
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        log.info("WebSocket disconnected sessionId={} closeStatus={}",
                event.getSessionId(), event.getCloseStatus());
    }
}
```

- [ ] **Step 3: Run context test**

Run:

```bash
cd realtime-gateway-service
./mvnw test
```

Expected: PASS after WebSocket beans initialize.

- [ ] **Step 4: Commit**

```bash
git add realtime-gateway-service/src/main/java/com/mobility/realtime/config/WebSocketConfig.java realtime-gateway-service/src/main/java/com/mobility/realtime/handler/WebSocketEventListener.java
git commit -m "feat: configure websocket broker"
```

---

## Task 5: Kafka Consumer Configuration

**Files:**
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/config/KafkaConsumerConfig.java`
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/config/KafkaErrorHandlerConfig.java`

- [ ] **Step 1: Add Kafka error handler**

Create `KafkaErrorHandlerConfig.java`:

```java
package com.mobility.realtime.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Kafka record failed after retries topic={} partition={} offset={} key={} value={}",
                        record.topic(), record.partition(), record.offset(), record.key(), record.value(), exception
                ),
                new FixedBackOff(1_000L, 3L)
        );
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retrying Kafka record topic={} offset={} attempt={}",
                        record.topic(), record.offset(), deliveryAttempt, ex));
        return errorHandler;
    }
}
```

- [ ] **Step 2: Add typed Kafka listener container factories**

Create `KafkaConsumerConfig.java`:

```java
package com.mobility.realtime.config;

import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, DriverLocationUpdatedEvent> driverLocationConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(),
                new StringDeserializer(),
                jsonDeserializer(DriverLocationUpdatedEvent.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DriverLocationUpdatedEvent> driverLocationKafkaListenerContainerFactory(
            ConsumerFactory<String, DriverLocationUpdatedEvent> driverLocationConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, DriverLocationUpdatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(driverLocationConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, AssignmentRequestedEvent> assignmentRequestedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(),
                new StringDeserializer(),
                jsonDeserializer(AssignmentRequestedEvent.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AssignmentRequestedEvent> assignmentRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, AssignmentRequestedEvent> assignmentRequestedConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, AssignmentRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(assignmentRequestedConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    private Map<String, Object> baseConsumerProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    private <T> JsonDeserializer<T> jsonDeserializer(Class<T> targetType) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(targetType);
        deserializer.addTrustedPackages("com.mobility.realtime.dto");
        deserializer.setUseTypeHeaders(false);
        return deserializer;
    }
}
```

- [ ] **Step 3: Run context test**

Run:

```bash
cd realtime-gateway-service
./mvnw test
```

Expected: PASS with Kafka listeners disabled in tests.

- [ ] **Step 4: Commit**

```bash
git add realtime-gateway-service/src/main/java/com/mobility/realtime/config/KafkaConsumerConfig.java realtime-gateway-service/src/main/java/com/mobility/realtime/config/KafkaErrorHandlerConfig.java
git commit -m "feat: configure kafka consumers"
```

---

## Task 6: Kafka Consumers With Unit Tests

**Files:**
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/kafka/DriverLocationConsumer.java`
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/kafka/DriverAssignmentConsumer.java`
- Create: `realtime-gateway-service/src/test/java/com/mobility/realtime/kafka/DriverLocationConsumerTest.java`
- Create: `realtime-gateway-service/src/test/java/com/mobility/realtime/kafka/DriverAssignmentConsumerTest.java`

- [ ] **Step 1: Write driver location consumer tests**

Create `DriverLocationConsumerTest.java`:

```java
package com.mobility.realtime.kafka;

import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import com.mobility.realtime.exception.InvalidRealtimeEventException;
import com.mobility.realtime.service.RealtimeBroadcastService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DriverLocationConsumerTest {

    private final RealtimeBroadcastService broadcastService = mock(RealtimeBroadcastService.class);
    private final DriverLocationConsumer consumer = new DriverLocationConsumer(broadcastService);

    @Test
    void consumeBroadcastsValidLocationEvent() {
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverId("driver_1")
                .rideId("ride_123")
                .latitude(28.6139)
                .longitude(77.2090)
                .timestamp(Instant.parse("2026-05-17T12:00:00Z"))
                .build();

        consumer.consume(event);

        verify(broadcastService).broadcastDriverLocation(event);
    }

    @Test
    void consumeRejectsMissingRideId() {
        DriverLocationUpdatedEvent event = DriverLocationUpdatedEvent.builder()
                .driverId("driver_1")
                .latitude(28.6139)
                .longitude(77.2090)
                .timestamp(Instant.parse("2026-05-17T12:00:00Z"))
                .build();

        assertThrows(InvalidRealtimeEventException.class, () -> consumer.consume(event));
    }
}
```

- [ ] **Step 2: Write assignment consumer tests**

Create `DriverAssignmentConsumerTest.java`:

```java
package com.mobility.realtime.kafka;

import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.exception.InvalidRealtimeEventException;
import com.mobility.realtime.service.RealtimeBroadcastService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DriverAssignmentConsumerTest {

    private final RealtimeBroadcastService broadcastService = mock(RealtimeBroadcastService.class);
    private final DriverAssignmentConsumer consumer = new DriverAssignmentConsumer(broadcastService);

    @Test
    void consumeBroadcastsValidAssignmentEvent() {
        AssignmentRequestedEvent event = AssignmentRequestedEvent.builder()
                .eventId("evt_1")
                .dispatchId(UUID.randomUUID())
                .rideId(UUID.randomUUID())
                .driverId(1L)
                .pickupLatitude(28.6139)
                .pickupLongitude(77.2090)
                .pickupLocation("Connaught Place")
                .expiresAt(Instant.parse("2026-05-17T12:00:15Z"))
                .build();

        consumer.consume(event);

        verify(broadcastService).broadcastAssignmentRequest(event);
    }

    @Test
    void consumeRejectsMissingDriverId() {
        AssignmentRequestedEvent event = AssignmentRequestedEvent.builder()
                .eventId("evt_1")
                .build();

        assertThrows(InvalidRealtimeEventException.class, () -> consumer.consume(event));
    }
}
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```bash
cd realtime-gateway-service
./mvnw -Dtest=DriverLocationConsumerTest,DriverAssignmentConsumerTest test
```

Expected: FAIL because the consumers do not exist.

- [ ] **Step 4: Add driver location consumer**

Create `DriverLocationConsumer.java`:

```java
package com.mobility.realtime.kafka;

import com.mobility.realtime.dto.DriverLocationUpdatedEvent;
import com.mobility.realtime.exception.InvalidRealtimeEventException;
import com.mobility.realtime.service.RealtimeBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverLocationConsumer {

    private final RealtimeBroadcastService broadcastService;

    @KafkaListener(
            topics = "${realtime.kafka.topics.driver-location-events}",
            containerFactory = "driverLocationKafkaListenerContainerFactory"
    )
    public void consume(DriverLocationUpdatedEvent event) {
        if (event == null || event.getRideId() == null || event.getRideId().isBlank()) {
            throw new InvalidRealtimeEventException("Driver location event must include rideId");
        }
        log.info("Consumed driver location event rideId={} driverId={} lat={} lng={}",
                event.getRideId(), event.getDriverId(), event.getLatitude(), event.getLongitude());
        broadcastService.broadcastDriverLocation(event);
    }
}
```

- [ ] **Step 5: Add driver assignment consumer**

Create `DriverAssignmentConsumer.java`:

```java
package com.mobility.realtime.kafka;

import com.mobility.realtime.dto.AssignmentRequestedEvent;
import com.mobility.realtime.exception.InvalidRealtimeEventException;
import com.mobility.realtime.service.RealtimeBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverAssignmentConsumer {

    private final RealtimeBroadcastService broadcastService;

    @KafkaListener(
            topics = "${realtime.kafka.topics.assignment-requested-events}",
            containerFactory = "assignmentRequestedKafkaListenerContainerFactory"
    )
    public void consume(AssignmentRequestedEvent event) {
        if (event == null || event.getDriverId() == null) {
            throw new InvalidRealtimeEventException("Assignment requested event must include driverId");
        }
        log.info("Consumed assignment requested event driverId={} rideId={} dispatchId={}",
                event.getDriverId(), event.getRideId(), event.getDispatchId());
        broadcastService.broadcastAssignmentRequest(event);
    }
}
```

- [ ] **Step 6: Run consumer tests**

Run:

```bash
cd realtime-gateway-service
./mvnw -Dtest=DriverLocationConsumerTest,DriverAssignmentConsumerTest test
```

Expected: PASS.

- [ ] **Step 7: Run full service tests**

Run:

```bash
cd realtime-gateway-service
./mvnw test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add realtime-gateway-service/src/main/java/com/mobility/realtime/kafka realtime-gateway-service/src/test/java/com/mobility/realtime/kafka
git commit -m "feat: consume realtime kafka events"
```

---

## Task 7: Info Controller And Manual WebSocket Client

**Files:**
- Create: `realtime-gateway-service/src/main/java/com/mobility/realtime/controller/RealtimeInfoController.java`
- Create: `realtime-gateway-service/src/main/resources/static/ws-test.html`

- [ ] **Step 1: Add lightweight info controller**

Create `RealtimeInfoController.java`:

```java
package com.mobility.realtime.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/realtime")
public class RealtimeInfoController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "realtime-gateway-service",
                "websocketEndpoint", "/ws",
                "riderSubscription", "/topic/trip/{rideId}",
                "driverSubscription", "/topic/driver/{driverId}",
                "stateless", true
        );
    }
}
```

- [ ] **Step 2: Add browser STOMP test client**

Create `realtime-gateway-service/src/main/resources/static/ws-test.html`:

```html
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Realtime Gateway Test Client</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 24px; background: #f7f8fa; color: #1f2937; }
        main { max-width: 960px; margin: 0 auto; }
        section { background: white; border: 1px solid #d8dee9; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
        label { display: block; font-weight: 700; margin: 12px 0 6px; }
        input { width: 100%; box-sizing: border-box; padding: 10px; border: 1px solid #b8c0cc; border-radius: 6px; }
        button { margin-top: 12px; margin-right: 8px; padding: 10px 14px; border: 0; border-radius: 6px; background: #2563eb; color: white; cursor: pointer; }
        button.secondary { background: #475569; }
        pre { background: #111827; color: #e5e7eb; padding: 16px; min-height: 220px; overflow: auto; border-radius: 8px; }
        .status { font-weight: 700; }
    </style>
</head>
<body>
<main>
    <h1>Realtime Gateway Test Client</h1>

    <section>
        <div>Status: <span id="status" class="status">disconnected</span></div>
        <button onclick="connect()">Connect</button>
        <button class="secondary" onclick="disconnect()">Disconnect</button>
    </section>

    <section>
        <label for="rideId">Ride ID</label>
        <input id="rideId" value="ride_123">
        <button onclick="subscribeTrip()">Subscribe Rider Trip</button>

        <label for="driverId">Driver ID</label>
        <input id="driverId" value="1">
        <button onclick="subscribeDriver()">Subscribe Driver Assignments</button>
    </section>

    <section>
        <h2>Messages</h2>
        <pre id="messages"></pre>
    </section>
</main>

<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
<script>
    let client;

    function log(message) {
        const output = document.getElementById('messages');
        output.textContent += `[${new Date().toISOString()}] ${message}\n`;
        output.scrollTop = output.scrollHeight;
    }

    function setStatus(value) {
        document.getElementById('status').textContent = value;
    }

    function connect() {
        client = new StompJs.Client({
            webSocketFactory: () => new SockJS('/ws'),
            reconnectDelay: 0,
            onConnect: () => {
                setStatus('connected');
                log('Connected to /ws');
            },
            onStompError: frame => log(`Broker error: ${frame.headers.message}`),
            onWebSocketClose: () => setStatus('disconnected')
        });
        client.activate();
    }

    function disconnect() {
        if (client) {
            client.deactivate();
            log('Disconnected');
        }
    }

    function subscribeTrip() {
        const rideId = document.getElementById('rideId').value.trim();
        const destination = `/topic/trip/${rideId}`;
        client.subscribe(destination, message => log(`${destination}: ${message.body}`));
        log(`Subscribed to ${destination}`);
    }

    function subscribeDriver() {
        const driverId = document.getElementById('driverId').value.trim();
        const destination = `/topic/driver/${driverId}`;
        client.subscribe(destination, message => log(`${destination}: ${message.body}`));
        log(`Subscribed to ${destination}`);
    }
</script>
</body>
</html>
```

- [ ] **Step 3: Run tests**

Run:

```bash
cd realtime-gateway-service
./mvnw test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add realtime-gateway-service/src/main/java/com/mobility/realtime/controller/RealtimeInfoController.java realtime-gateway-service/src/main/resources/static/ws-test.html
git commit -m "feat: add realtime test client"
```

---

## Task 8: Docker Port Fix And Test Documentation

**Files:**
- Modify: `docker/docker-compose.yml`
- Create: `realtime-gateway-service/README.md`

- [ ] **Step 1: Move Kafka UI host port**

In `docker/docker-compose.yml`, change Kafka UI ports from:

```yaml
    ports:
      - "8085:8080"
```

to:

```yaml
    ports:
      - "8090:8080"
```

- [ ] **Step 2: Add service README**

Create `realtime-gateway-service/README.md`:

```markdown
# Realtime Gateway Service

Stateless Kafka to WebSocket/STOMP gateway for Smart Mobility.

## Responsibilities

- Consume `driver-location-events` from Kafka and broadcast to `/topic/trip/{rideId}`.
- Consume `assignment-requested` from Kafka and broadcast to `/topic/driver/{driverId}`.
- Log WebSocket connect, subscribe, and disconnect events.

The service does not ingest driver GPS directly, persist state, replay missed messages, authenticate users, or own ride/driver lifecycle state.

## Run Locally

Start Kafka:

```bash
docker compose -f ../docker/docker-compose.yml up -d zookeeper kafka kafka-ui
```

Kafka is available at `localhost:9092`.
Kafka UI is available at `http://localhost:8090`.

Create topics:

```bash
docker compose -f ../docker/docker-compose.yml exec kafka kafka-topics \
  --bootstrap-server kafka:29092 \
  --create \
  --if-not-exists \
  --topic driver-location-events \
  --partitions 3 \
  --replication-factor 1

docker compose -f ../docker/docker-compose.yml exec kafka kafka-topics \
  --bootstrap-server kafka:29092 \
  --create \
  --if-not-exists \
  --topic assignment-requested \
  --partitions 3 \
  --replication-factor 1
```

Start the service:

```bash
./mvnw spring-boot:run
```

Open the browser client:

```text
http://localhost:8085/ws-test.html
```

## WebSocket Subscriptions

Rider trip tracking:

```text
/topic/trip/ride_123
```

Driver assignment notifications:

```text
/topic/driver/1
```

## Publish Sample Location Event

```bash
docker compose -f ../docker/docker-compose.yml exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:29092 \
  --topic driver-location-events <<'JSON'
{"driverId":"driver_1","rideId":"ride_123","latitude":28.6139,"longitude":77.2090,"speed":42.0,"heading":120.0,"timestamp":"2026-05-17T12:00:00Z"}
JSON
```

Expected: the rider subscription `/topic/trip/ride_123` receives the JSON event.

## Publish Sample Assignment Event

```bash
docker compose -f ../docker/docker-compose.yml exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:29092 \
  --topic assignment-requested <<'JSON'
{"eventId":"evt_1","eventType":"ASSIGNMENT_REQUESTED","dispatchId":"11111111-1111-1111-1111-111111111111","rideId":"22222222-2222-2222-2222-222222222222","driverId":1,"pickupLatitude":28.6139,"pickupLongitude":77.2090,"pickupLocation":"Connaught Place","expiresAt":"2026-05-17T12:00:15Z"}
JSON
```

Expected: the driver subscription `/topic/driver/1` receives the JSON event.

## Postman WebSocket Setup

Postman raw WebSocket support does not speak STOMP automatically. Use a STOMP-capable client where available, or use `ws-test.html`.

For manual STOMP frames against `ws://localhost:8085/ws`, connect and send:

```text
CONNECT
accept-version:1.2
heart-beat:10000,10000

^@
```

Then subscribe:

```text
SUBSCRIBE
id:sub-0
destination:/topic/trip/ride_123

^@
```

The `^@` marker represents the STOMP null terminator.
```

- [ ] **Step 3: Run a compose config validation**

Run:

```bash
docker compose -f docker/docker-compose.yml config >/tmp/smart-mobility-compose.yml
```

Expected: command exits 0 and renders the compose file.

- [ ] **Step 4: Commit**

```bash
git add docker/docker-compose.yml realtime-gateway-service/README.md
git commit -m "docs: add realtime gateway test flow"
```

---

## Task 9: End-To-End Verification

**Files:**
- No new files expected.

- [ ] **Step 1: Run full test suite for service**

Run:

```bash
cd realtime-gateway-service
./mvnw test
```

Expected: PASS.

- [ ] **Step 2: Start Kafka stack**

Run from repo root:

```bash
docker compose -f docker/docker-compose.yml up -d zookeeper kafka kafka-ui
```

Expected: containers start; Kafka UI is available at `http://localhost:8090`.

- [ ] **Step 3: Create topics**

Run from `realtime-gateway-service`:

```bash
docker compose -f ../docker/docker-compose.yml exec kafka kafka-topics \
  --bootstrap-server kafka:29092 \
  --create \
  --if-not-exists \
  --topic driver-location-events \
  --partitions 3 \
  --replication-factor 1

docker compose -f ../docker/docker-compose.yml exec kafka kafka-topics \
  --bootstrap-server kafka:29092 \
  --create \
  --if-not-exists \
  --topic assignment-requested \
  --partitions 3 \
  --replication-factor 1
```

Expected: each command reports topic created or already exists.

- [ ] **Step 4: Start service**

Run:

```bash
cd realtime-gateway-service
./mvnw spring-boot:run
```

Expected: service starts on port `8085` and logs Kafka consumer startup.

- [ ] **Step 5: Verify browser client**

Open:

```text
http://localhost:8085/ws-test.html
```

Click `Connect`.
Subscribe rider to `ride_123`.
Subscribe driver to `1`.

Expected: browser shows connected and subscription log entries; service logs connect and subscribe events.

- [ ] **Step 6: Publish location event**

Run from `realtime-gateway-service`:

```bash
docker compose -f ../docker/docker-compose.yml exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:29092 \
  --topic driver-location-events <<'JSON'
{"driverId":"driver_1","rideId":"ride_123","latitude":28.6139,"longitude":77.2090,"speed":42.0,"heading":120.0,"timestamp":"2026-05-17T12:00:00Z"}
JSON
```

Expected: browser receives event under `/topic/trip/ride_123`.

- [ ] **Step 7: Publish assignment event**

Run from `realtime-gateway-service`:

```bash
docker compose -f ../docker/docker-compose.yml exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:29092 \
  --topic assignment-requested <<'JSON'
{"eventId":"evt_1","eventType":"ASSIGNMENT_REQUESTED","dispatchId":"11111111-1111-1111-1111-111111111111","rideId":"22222222-2222-2222-2222-222222222222","driverId":1,"pickupLatitude":28.6139,"pickupLongitude":77.2090,"pickupLocation":"Connaught Place","expiresAt":"2026-05-17T12:00:15Z"}
JSON
```

Expected: browser receives event under `/topic/driver/1`.

- [ ] **Step 8: Stop local app and leave containers only if user wants them**

Stop the Spring Boot process with `Ctrl+C`.

If cleanup is requested:

```bash
docker compose -f docker/docker-compose.yml down
```

- [ ] **Step 9: Final status**

Summarize:

- Tests run and result.
- Whether manual Kafka/WebSocket verification was completed.
- Files changed.
- Any environmental limitations.

---

## Self-Review Checklist

- Spec coverage: Tasks cover project setup, YAML config, DTOs, WebSocket config, Kafka config, consumers, broadcast service, connection events, Docker Kafka UI port, browser client, README, automated tests, and manual end-to-end steps.
- Scope: No Redis pub/sub, auth, replay, clustering, persistence, sticky sessions, Netty, or distributed routing tasks are included.
- Type consistency: `driverId` is `String` for location events and `Long` for assignment events to match current contracts; `rideId` is `String` for location events and `UUID` for assignment events.
- Port consistency: Realtime Gateway uses `8085`; Kafka UI uses `8090`; Location Service remains `8086`.
