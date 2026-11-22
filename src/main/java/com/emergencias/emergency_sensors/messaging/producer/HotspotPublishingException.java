package com.emergencias.emergency_sensors.messaging.producer;

/**
 * A hotspot could not be sent or Kafka did not confirm it in time. It is not
 * marked as sent, so the next query publishes it again.
 */
public class HotspotPublishingException extends RuntimeException {

    public HotspotPublishingException(String hotspotId, Throwable cause) {
        super("Kafka no confirmó el foco " + hotspotId, cause);
    }
}
