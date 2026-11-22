package com.emergencias.emergency_sensors.messaging.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Names of the Kafka topics owned by this service.
 * <p>
 * {@code emergency-events} is not declared here: the core service owns it and
 * creates it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    public static final String SATELLITE_HOTSPOTS = "sensor-satellite-hotspots";
}
