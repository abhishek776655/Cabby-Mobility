package com.smartmobility.user.consumer;

import com.smartmobility.user.dto.AuthRegisteredEvent;
import com.smartmobility.user.entity.UserEntity;
import com.smartmobility.user.repository.UserRepository;
import com.smartmobility.user.producer.UserEventPublisher;
import com.smartmobility.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRegisteredConsumer {

    private final UserRepository userRepository;
    private final UserEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auth.registered", groupId = "user-service-group")
    @Transactional
    public void consumeAuthRegisteredEvent(String message) {
        log.info("Received auth.registered event: {}", message);
        try {
            AuthRegisteredEvent event = objectMapper.readValue(message, AuthRegisteredEvent.class);
            
            // Check if user already exists just in case of duplicate delivery
            if (userRepository.findById(event.getId()).isPresent()) {
                log.info("User with ID {} already exists, ignoring duplicate event", event.getId());
                return;
            }

            Set<Role> mappedRoles = event.getRoles().stream()
                    .map(Role::valueOf)
                    .collect(Collectors.toSet());

            UserEntity user = UserEntity.builder()
                    .id(event.getId())
                    .email(event.getEmail())
                    .roles(mappedRoles)
                    .createdAt(LocalDateTime.now())
                    .build();

            UserEntity savedUser = userRepository.save(user);
            log.info("Successfully created User Profile for ID: {}", savedUser.getId());

            // Continue the chain by publishing user.created for Downstream services (Driver/Rider)
            eventPublisher.publishUserCreated(savedUser);

        } catch (Exception e) {
            log.error("Failed to process auth.registered event", e);
            throw new RuntimeException("Error processing auth.registered event", e);
        }
    }
}
