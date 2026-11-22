package com.emergencias.emergency_sensors.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka container used by the integration tests. It is a Spring bean, so it
 * lives as long as the cached context, and {@code @ServiceConnection} points
 * the application at it instead of the address in the properties.
 * <p>
 * The image is the same version used for development.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestContainers {

    static final String KAFKA_IMAGE = "apache/kafka:4.3.1";

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }
}
