package com.smartmobility.user_service.producer;


import com.smartmobility.user_service.entity.UserEntity;
import com.smartmobility.user_service.event.UserCreatedEvent;
import com.smartmobility.user_service.service.impl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "user.created";
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Async
    public void publishUserCreated(UserEntity user) {

        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .roles(
                        user.getRoles()
                                .stream()
                                .map(Enum::name)
                                .collect(Collectors.toSet())
                )
                .build();
        kafkaTemplate.send(TOPIC, event)
                .whenComplete((result, ex) -> {

                    if (ex != null) {
                        log.error("❌ Kafka SEND FAILED", ex);
                    } else {
                        log.info("✅ Kafka SEND SUCCESS: {}", result.getRecordMetadata());
                    }
                });}
}