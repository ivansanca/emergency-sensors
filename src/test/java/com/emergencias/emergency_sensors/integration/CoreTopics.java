package com.emergencias.emergency_sensors.integration;

import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics that the core service creates in a real deployment. The tests create
 * them themselves instead of relying on the broker creating topics on first
 * use, which the development setup is going to turn off.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CoreTopics {

    @Bean
    NewTopic emergencyEventsTopic() {
        return TopicBuilder.name(KafkaTopics.EMERGENCY_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
