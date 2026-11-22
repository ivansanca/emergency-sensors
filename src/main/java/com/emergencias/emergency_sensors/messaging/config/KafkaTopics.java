package com.emergencias.emergency_sensors.messaging.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Names of the Kafka topics used by this service.
 * <p>
 * The service declares and creates its own topics (see
 * {@link KafkaTopicsConfig}). {@code emergency-events} is only written here:
 * the core service owns it and creates it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    public static final String SATELLITE_HOTSPOTS = "sensor-satellite-hotspots";
    public static final String SATELLITE_HOTSPOTS_DLT = "sensor-satellite-hotspots-dlt";

    public static final String EMERGENCY_EVENTS = "emergency-events";
}
