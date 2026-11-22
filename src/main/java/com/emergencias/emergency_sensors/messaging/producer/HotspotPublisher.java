package com.emergencias.emergency_sensors.messaging.producer;

import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes raw hotspots to {@code sensor-satellite-hotspots} and waits until
 * Kafka confirms each one.
 * <p>
 * The key is the detection id, so every copy of the same detection lands in
 * the same partition. Waiting for the confirmation lets the caller mark the
 * hotspot as sent only when it really is. How long it waits, and why a
 * hotspot reported as failed may still arrive, is explained in
 * {@link ConfirmedSend}.
 */
@Component
@RequiredArgsConstructor
public class HotspotPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public void publish(FirmsHotspot hotspot) {
        String message = jsonMapper.writeValueAsString(hotspot);

        ConfirmedSend.sendAndWait(
                kafkaTemplate,
                KafkaTopics.SATELLITE_HOTSPOTS,
                hotspot.id(),
                message,
                cause -> new HotspotPublishingException(hotspot.id(), cause));
    }
}
