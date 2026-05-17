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

    @Value("${spring.kafka.listener.auto-startup:true}")
    private boolean autoStartup;

    @Bean
    public ConsumerFactory<String, DriverLocationUpdatedEvent> driverLocationConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(),
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(jsonDeserializer(DriverLocationUpdatedEvent.class))
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
        factory.setAutoStartup(autoStartup);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, AssignmentRequestedEvent> assignmentRequestedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(),
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(jsonDeserializer(AssignmentRequestedEvent.class))
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
        factory.setAutoStartup(autoStartup);
        return factory;
    }

    private Map<String, Object> baseConsumerProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
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
