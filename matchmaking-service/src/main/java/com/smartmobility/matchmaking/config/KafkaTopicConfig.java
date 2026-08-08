package com.smartmobility.matchmaking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic driverAssignedTopic() {
        return TopicBuilder.name("driver-assigned").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic matchmakingFailedTopic() {
        return TopicBuilder.name("matchmaking-failed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic assignmentRequestedTopic() {
        return TopicBuilder.name("assignment-requested").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic driverAssignmentFailedTopic() {
        return TopicBuilder.name("driver-assignment-failed").partitions(3).replicas(1).build();
    }
}
