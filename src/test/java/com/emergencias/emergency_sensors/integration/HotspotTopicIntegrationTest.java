package com.emergencias.emergency_sensors.integration;

import com.emergencias.emergency_sensors.ingestion.FirmsPoller;
import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import com.emergencias.emergency_sensors.messaging.producer.HotspotPublisher;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaAdmin;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the raw hotspot topic against a real broker: it is created with its
 * partitions and a published hotspot can be read back as JSON.
 */
@IntegrationTest
class HotspotTopicIntegrationTest {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private HotspotPublisher publisher;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private KafkaContainer kafka;

    @Value("${sensors.kafka.hotspot-partitions}")
    private int partitions;

    @Test
    void rawTopicIsCreatedWithItsPartitions() {
        Map<String, TopicDescription> topics =
                kafkaAdmin.describeTopics(KafkaTopics.SATELLITE_HOTSPOTS);

        assertThat(topics.get(KafkaTopics.SATELLITE_HOTSPOTS).partitions())
                .hasSize(partitions);
    }

    @Test
    void publishedHotspotArrivesKeyedByItsId() {
        FirmsHotspot hotspot = new FirmsHotspot(
                "FIRMS-integration-test", "VIIRS_NOAA21_NRT", 43.10741, -4.19757,
                316.59, 0.39, 0.36, "2026-03-21", "0219", "N21", "VIIRS", "n",
                "2.0NRT", 277.4, 1.54, "N", null, Instant.parse("2026-03-22T03:00:00Z"));

        publisher.publish(hotspot);

        try (TopicReader reader = new TopicReader(
                kafka.getBootstrapServers(), KafkaTopics.SATELLITE_HOTSPOTS)) {

            ConsumerRecord<String, String> record = reader.awaitFirst(
                    KafkaTopics.SATELLITE_HOTSPOTS, "FIRMS-integration-test");

            assertThat(record.value())
                    .contains("\"id\":\"FIRMS-integration-test\"")
                    .contains("\"dataset\":\"VIIRS_NOAA21_NRT\"");
        }
    }

    @Test
    void ingestionIsOffInTests() {
        assertThat(context.getBeanProvider(FirmsPoller.class).getIfAvailable()).isNull();
    }
}
