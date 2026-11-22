package com.emergencias.emergency_sensors.integration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reads topics from the beginning with its own consumer group, the way an
 * external subscriber would, and keeps every record it has seen.
 */
final class TopicReader implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> seen = new ArrayList<>();

    TopicReader(String bootstrapServers, String... topics) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG,
                "integration-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topics));
    }

    ConsumerRecord<String, String> awaitFirst(String topic, String key) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();

        while (System.nanoTime() - deadline < 0) {
            List<ConsumerRecord<String, String>> found = recordsFor(topic, key);
            if (!found.isEmpty()) {
                return found.getFirst();
            }
            consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
        }

        return fail("No message with key " + key + " in " + topic);
    }

    List<ConsumerRecord<String, String>> recordsFor(String topic, String key) {
        return seen.stream()
                .filter(record -> record.topic().equals(topic))
                .filter(record -> Objects.equals(record.key(), key))
                .toList();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
