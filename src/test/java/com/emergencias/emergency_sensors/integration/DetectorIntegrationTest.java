package com.emergencias.emergency_sensors.integration;

import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import com.emergencias.emergency_sensors.messaging.event.TestHotspots.Place;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.LOS_TOJOS;
import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.STEEL_MILL;
import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.at;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the detector against a real broker: a hotspot written to the raw topic
 * comes out in {@code emergency-events}, is dropped, or ends up in the dead
 * letter topic.
 * <p>
 * To prove that a hotspot was dropped without waiting an arbitrary time, it
 * is written to the same partition as a forest hotspot sent right after it.
 * Kafka keeps the order within a partition, so once the forest event has
 * arrived the dropped one has already been processed.
 */
@IntegrationTest
class DetectorIntegrationTest {

    private static final int PARTITION = 0;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    void deadLetterTopicIsCreated() {
        Map<String, TopicDescription> topics =
                kafkaAdmin.describeTopics(KafkaTopics.SATELLITE_HOTSPOTS_DLT);

        assertThat(topics.get(KafkaTopics.SATELLITE_HOTSPOTS_DLT).partitions()).hasSize(1);
    }

    @Test
    void forestHotspotReachesTheCoreTopic() throws Exception {
        FirmsHotspot forest = recent("FIRMS-it-forest", LOS_TOJOS);
        send(forest);

        try (TopicReader reader = reader()) {
            String event = reader.awaitFirst(KafkaTopics.EMERGENCY_EVENTS, forest.id()).value();

            assertThat(event)
                    .contains("\"id\":\"FIRMS-it-forest\"")
                    .contains("\"type\":\"FOREST_FIRE\"")
                    .contains("\"severity\":0")
                    .contains("\"zoneCode\":\"39086\"")
                    .contains("\"source\":\"FIRMS\"");
        }
    }

    @Test
    void hotspotAtTheSteelMillIsNotSent() throws Exception {
        FirmsHotspot mill = recent("FIRMS-it-mill", STEEL_MILL);
        FirmsHotspot forest = recent("FIRMS-it-after-mill", LOS_TOJOS);
        send(mill);
        send(forest);

        try (TopicReader reader = reader()) {
            reader.awaitFirst(KafkaTopics.EMERGENCY_EVENTS, forest.id());

            assertThat(reader.recordsFor(KafkaTopics.EMERGENCY_EVENTS, mill.id())).isEmpty();
        }
    }

    @Test
    void unreadableMessageGoesToTheDeadLetterTopic() throws Exception {
        send("FIRMS-it-broken", "{not json");

        try (TopicReader reader = reader()) {
            ConsumerRecord<String, String> rejected =
                    reader.awaitFirst(KafkaTopics.SATELLITE_HOTSPOTS_DLT, "FIRMS-it-broken");

            assertThat(rejected.value()).isEqualTo("{not json");
        }
    }

    @Test
    void hotspotWithoutPositionGoesToTheDeadLetterTopic() throws Exception {
        FirmsHotspot complete = recent("FIRMS-it-no-position", LOS_TOJOS);
        FirmsHotspot withoutPosition = new FirmsHotspot(
                complete.id(), complete.dataset(), null, null, null, null, null,
                complete.acqDate(), complete.acqTime(), complete.satellite(), complete.instrument(),
                complete.confidence(), complete.version(), null, null, complete.daynight(), null,
                complete.fetchedAt());
        send(withoutPosition);

        try (TopicReader reader = reader()) {
            reader.awaitFirst(KafkaTopics.SATELLITE_HOTSPOTS_DLT, withoutPosition.id());

            assertThat(reader.recordsFor(KafkaTopics.EMERGENCY_EVENTS, withoutPosition.id())).isEmpty();
        }
    }

    /** Observed an hour ago, well within the maximum age. */
    private static FirmsHotspot recent(String id, Place place) {
        return at(id, place, "n", Instant.now().minus(Duration.ofHours(1)));
    }

    private void send(FirmsHotspot hotspot) throws Exception {
        send(hotspot.id(), jsonMapper.writeValueAsString(hotspot));
    }

    private void send(String key, String message) throws Exception {
        kafkaTemplate.send(KafkaTopics.SATELLITE_HOTSPOTS, PARTITION, key, message)
                .get(10, TimeUnit.SECONDS);
    }

    private TopicReader reader() {
        return new TopicReader(kafka.getBootstrapServers(),
                KafkaTopics.EMERGENCY_EVENTS, KafkaTopics.SATELLITE_HOTSPOTS_DLT);
    }
}
