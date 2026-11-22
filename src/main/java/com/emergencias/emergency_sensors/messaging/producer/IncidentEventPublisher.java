package com.emergencias.emergency_sensors.messaging.producer;

import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import com.emergencias.emergency_sensors.messaging.event.IncidentEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.errors.RecordBatchTooLargeException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Sends incident events to {@code emergency-events}, the topic read by the
 * core service, and waits until Kafka confirms each one.
 * <p>
 * The key is the event id, as in the core's own producer. Waiting for the
 * confirmation means that the detector only moves on to the next hotspot once
 * the event is safely in Kafka; if it fails, the hotspot is processed again.
 * <p>
 * A failure is reported as {@link IncidentEventRejectedException} only when
 * it comes from the event itself and can never work; any other failure,
 * authorisation errors included, is {@link IncidentEventPublishingException}.
 */
@Component
@RequiredArgsConstructor
public class IncidentEventPublisher {

    private static final List<Class<? extends Throwable>> EVENT_FAILURES = List.of(
            RecordTooLargeException.class,
            RecordBatchTooLargeException.class,
            InvalidRecordException.class,
            SerializationException.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public void publish(IncidentEvent event) {
        String message = jsonMapper.writeValueAsString(event);

        ConfirmedSend.sendAndWait(
                kafkaTemplate,
                KafkaTopics.EMERGENCY_EVENTS,
                event.id(),
                message,
                cause -> isEventFailure(cause)
                        ? new IncidentEventRejectedException(event.id(), cause)
                        : new IncidentEventPublishingException(event.id(), cause));
    }

    /** The whole chain is inspected because Spring wraps the Kafka error. */
    static boolean isEventFailure(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            Throwable current = cause;
            if (EVENT_FAILURES.stream().anyMatch(type -> type.isInstance(current))) {
                return true;
            }
        }
        return false;
    }
}
