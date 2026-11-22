package com.emergencias.emergency_sensors.messaging.producer;

import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes raw hotspots to {@code sensor-satellite-hotspots} and waits until
 * Kafka confirms each one.
 * <p>
 * The key is the detection id, so every copy of the same detection lands in
 * the same partition. Waiting for the confirmation lets the caller mark the
 * hotspot as sent only when it really is.
 * <p>
 * The wait is longer than {@code delivery.timeout.ms}, so the producer stops
 * retrying before this class gives up. A request already on the wire may still
 * be written by the broker, so a hotspot reported as failed can arrive twice;
 * consumers tolerate it because the key is the detection id. {@code send()}
 * itself may block up to {@code max.block.ms} before the wait starts.
 */
@Component
@RequiredArgsConstructor
public class HotspotPublisher {

    static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public void publish(FirmsHotspot hotspot) {
        String message = jsonMapper.writeValueAsString(hotspot);

        try {
            kafkaTemplate.send(KafkaTopics.SATELLITE_HOTSPOTS, hotspot.id(), message)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HotspotPublishingException(hotspot.id(), exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            // RuntimeException: send() throws instead of failing the future
            // when the broker is down or the thread is interrupted.
            throw new HotspotPublishingException(hotspot.id(), exception);
        }
    }
}
