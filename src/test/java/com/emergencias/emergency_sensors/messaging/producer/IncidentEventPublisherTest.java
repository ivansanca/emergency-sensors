package com.emergencias.emergency_sensors.messaging.producer;

import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import com.emergencias.emergency_sensors.messaging.event.IncidentEvent;
import com.emergencias.emergency_sensors.messaging.event.IncidentSource;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the message sent to the core's topic and how send failures are
 * reported.
 */
class IncidentEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final IncidentEventPublisher publisher =
            new IncidentEventPublisher(kafkaTemplate, JsonMapper.builder().build());

    private final IncidentEvent event = new IncidentEvent(
            1, "FIRMS-abc", "FOREST_FIRE", 0, "39086", 43.10741, -4.19757,
            Instant.parse("2026-11-22T03:02:00Z"), IncidentSource.FIRMS, "Foco");

    @Test
    void publishesToEmergencyEventsKeyedByEventId() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.EMERGENCY_EVENTS), eq("FIRMS-abc"), message.capture());
        assertThat(message.getValue())
                .contains("\"id\":\"FIRMS-abc\"")
                .contains("\"zoneCode\":\"39086\"")
                .contains("\"source\":\"FIRMS\"");
    }

    @Test
    void unconfirmedSendBecomesIncidentEventPublishingException() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IncidentEventPublishingException.class)
                .hasMessage("Kafka no confirmó el evento del foco FIRMS-abc")
                .hasCauseInstanceOf(ExecutionException.class);
    }

    @Test
    void synchronousSendFailureBecomesIncidentEventPublishingException() {
        KafkaException failure = new KafkaException("Send failed");
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(failure);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IncidentEventPublishingException.class)
                .hasCause(failure);
    }

    @Test
    void eventTooLargeBecomesIncidentEventRejectedException() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RecordTooLargeException("too large")));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IncidentEventRejectedException.class)
                .hasMessage("Kafka rechazó el evento del foco FIRMS-abc");
    }

    @Test
    void writeNotAuthorisedIsNotARejectionOfTheEvent() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TopicAuthorizationException("emergency-events")));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IncidentEventPublishingException.class);
    }
}
