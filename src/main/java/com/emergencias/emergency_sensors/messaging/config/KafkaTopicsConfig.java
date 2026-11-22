package com.emergencias.emergency_sensors.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics of this service so that they are created at startup when
 * missing. Creation is idempotent. In production the topics would be
 * provisioned by the infrastructure team instead.
 * <p>
 * The raw hotspot topic gets several partitions from the start: the number of
 * partitions is the maximum number of detector instances that can share the
 * work, and adding partitions later reshuffles the keys. The dead letter topic
 * only receives the odd broken message, so one partition is enough.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic satelliteHotspotsTopic(
            @Value("${sensors.kafka.hotspot-partitions}") int partitions) {

        return TopicBuilder.name(KafkaTopics.SATELLITE_HOTSPOTS)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic satelliteHotspotsDeadLetterTopic() {
        return TopicBuilder.name(KafkaTopics.SATELLITE_HOTSPOTS_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
