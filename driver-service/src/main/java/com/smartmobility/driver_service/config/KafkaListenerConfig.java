package com.smartmobility.driver_service.config;


import com.smartmobility.driver_service.event.UserCreatedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

@Configuration
public class KafkaListenerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserCreatedEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, UserCreatedEvent> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {

        ConcurrentKafkaListenerContainerFactory<String, UserCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Attach retry + DLQ
        factory.setCommonErrorHandler(errorHandler);

        // ⚡ Parallel consumption (tune based on partitions)
        factory.setConcurrency(3);

        // keep single record processing
        factory.setBatchListener(false);

        return factory;
    }
}
