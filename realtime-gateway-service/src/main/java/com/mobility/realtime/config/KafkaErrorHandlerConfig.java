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
