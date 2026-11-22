package com.emergencias.emergency_sensors.messaging.producer;

/**
 * Kafka rejected the event itself, for instance because it is too large, so
 * sending it again can never work. The detector's error handler sends the
 * hotspot to the dead letter topic.
 * <p>
 * It is a different class from {@link IncidentEventPublishingException} on
 * purpose: Spring Kafka only reconsiders how to retry a record when the type
 * of the exception changes.
 */
public class IncidentEventRejectedException extends RuntimeException {

    public IncidentEventRejectedException(String eventId, Throwable cause) {
        super("Kafka rechazó el evento del foco " + eventId, cause);
    }
}
