package com.smartmobility.cab.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic rideRequestedTopic() {
        return TopicBuilder.name("ride-requested").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic assignmentAcceptedTopic() {
        return TopicBuilder.name("assignment-accepted").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic assignmentRejectedTopic() {
        return TopicBuilder.name("assignment-rejected").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideCompletedTopic() {
        return TopicBuilder.name("ride-completed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideCancelledTopic() {
        return TopicBuilder.name("ride-cancelled").partitions(3).replicas(1).build();
    }
}
