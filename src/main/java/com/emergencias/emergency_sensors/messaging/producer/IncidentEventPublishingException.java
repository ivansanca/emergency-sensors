package com.emergencias.emergency_sensors.messaging.producer;

/**
 * An incident event could not be sent or Kafka did not confirm it in time,
 * for a reason that does not depend on the event: the broker or the topic is
 * not available, the service is not allowed to write... The detector retries
 * it without limit.
 */
public class IncidentEventPublishingException extends RuntimeException {

    public IncidentEventPublishingException(String eventId, Throwable cause) {
        super("Kafka no confirmó el evento del foco " + eventId, cause);
    }
}
