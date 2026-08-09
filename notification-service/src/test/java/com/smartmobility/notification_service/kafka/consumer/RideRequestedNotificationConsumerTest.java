package com.smartmobility.notification_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmobility.notification_service.event.RideRequestedEvent;
import com.smartmobility.notification_service.service.NotificationDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RideRequestedNotificationConsumerTest {

    @Mock
    private NotificationDeliveryService deliveryService;

    // Use actual Jackson ObjectMapper to test deserialization
    private tools.jackson.databind.ObjectMapper objectMapper;

    private RideRequestedNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new tools.jackson.databind.ObjectMapper();
        consumer = new RideRequestedNotificationConsumer(objectMapper, deliveryService);
    }

    @Test
    void testConsume_ValidMessage() {
        // Arrange
        String message = """
            {
              "eventId": "evt-123",
              "rideId": "123e4567-e89b-12d3-a456-426614174000",
              "riderUserId": 1,
              "pickupLat": 12.0,
              "pickupLng": 77.0,
              "timestamp": "2023-01-01T12:00:00Z"
            }
        """;

        // Act
        consumer.consume(message);

        // Assert
        verify(deliveryService, times(1)).deliver(
                eq("evt-123"),
                eq(1L),
                eq("RIDE_REQUESTED"),
                eq("PUSH"),
                eq("We're finding you a driver")
        );
    }

    @Test
    void testConsume_InvalidMessage() {
        // Arrange
        String message = "invalid json";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(message));
        verify(deliveryService, never()).deliver(anyString(), anyLong(), anyString(), anyString(), anyString());
    }
}
