package com.emergencias.emergency_sensors.messaging.consumer;

import com.emergencias.emergency_sensors.detection.Decision;
import com.emergencias.emergency_sensors.detection.DetectionMetrics;
import com.emergencias.emergency_sensors.detection.HotspotEvaluator;
import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Fire detector: reads the raw hotspots, keeps the ones that look like fires
 * and sends them to the core service as incident events.
 * <p>
 * All instances share the {@code emergency-sensors-detector} group, so Kafka
 * splits the partitions of the raw topic among them; with 6 partitions up to
 * 6 instances work in parallel.
 * <p>
 * Each event is confirmed by Kafka before the hotspot counts as processed, and
 * offsets are committed after that. If the service stops halfway, some
 * hotspots are processed again; the core ignores the repeated events because
 * their id is the detection id.
 * <p>
 * Exceptions are deliberately not caught: the error handler decides whether
 * to retry the hotspot or to send it to the dead letter topic.
 */
@Slf4j
@RequiredArgsConstructor
public class HotspotConsumer {

    public static final String GROUP_ID = "emergency-sensors-detector";
    public static final String LISTENER_ID = "hotspot-detector";

    private final JsonMapper jsonMapper;
    private final HotspotEvaluator evaluator;
    private final IncidentEventPublisher publisher;
    private final DetectionMetrics metrics;
    private final Clock clock;

    @KafkaListener(
            id = LISTENER_ID,
            groupId = GROUP_ID,
            topics = KafkaTopics.SATELLITE_HOTSPOTS)
    public void consume(String message) {
        FirmsHotspot hotspot = jsonMapper.readValue(message, FirmsHotspot.class);
        Decision decision = evaluator.evaluate(hotspot, clock.instant());

        if (decision instanceof Decision.Accepted accepted) {
            publisher.publish(accepted.event());
            log.info("Foco {} enviado al núcleo: {}",
                    hotspot.id(), accepted.event().description());
        } else if (decision instanceof Decision.Discarded discarded) {
            // Debug: the static heat sources alone are seen on every pass.
            log.debug("Foco {} descartado: {}", hotspot.id(), discarded);
        }

        metrics.record(decision);
    }
}
