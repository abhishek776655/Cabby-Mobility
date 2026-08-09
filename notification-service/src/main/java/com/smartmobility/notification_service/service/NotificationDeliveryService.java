package com.smartmobility.notification_service.service;

public interface NotificationDeliveryService {
    void deliver(String eventId, Long userId, String eventType, String channel, String message);
}
