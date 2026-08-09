package com.smartmobility.notification_service.service;

import com.smartmobility.notification_service.entity.NotificationEntity;
import com.smartmobility.notification_service.repository.NotificationRepository;
import com.smartmobility.notification_service.service.impl.NotificationDeliveryServiceImpl;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationDeliveryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    
    @Mock
    private MeterRegistry meterRegistry;
    
    @Mock
    private Counter mockCounter;

    private NotificationDeliveryServiceImpl deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new NotificationDeliveryServiceImpl(notificationRepository, meterRegistry);
        lenient().when(meterRegistry.counter(anyString())).thenReturn(mockCounter);
    }

    @Test
    void testDeliver_Success() {
        // Arrange
        String eventId = "evt-123";
        String eventType = "RIDE_REQUESTED";
        Long recipientId = 1L;
        String payload = "We're finding you a driver";
        String channel = "PUSH";

        when(notificationRepository.existsByEventId(eventId)).thenReturn(false);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        deliveryService.deliver(eventId, recipientId, eventType, channel, payload);

        // Assert
        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        NotificationEntity savedEntity = captor.getValue();
        assertEquals(eventId, savedEntity.getEventId());
        assertEquals(eventType, savedEntity.getEventType());
        assertEquals(recipientId, savedEntity.getUserId());
        assertEquals(payload, savedEntity.getMessage());
        assertEquals(channel, savedEntity.getChannel());
        assertEquals("SENT", savedEntity.getStatus());
        assertNotNull(savedEntity.getCreatedAt());
    }

    @Test
    void testDeliver_Idempotency_DuplicateEvent() {
        // Arrange
        String eventId = "evt-456";
        String eventType = "RIDE_CANCELLED";
        Long recipientId = 2L;
        String payload = "{}";
        String channel = "PUSH";

        when(notificationRepository.existsByEventId(eventId)).thenReturn(false);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        // Act
        deliveryService.deliver(eventId, recipientId, eventType, channel, payload);

        // Assert
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
    }
}
