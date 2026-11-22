package com.emergencias.emergency_sensors.messaging.producer;

import com.emergencias.emergency_sensors.messaging.config.KafkaTopics;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the message sent to Kafka and how each kind of send failure is
 * reported.
 */
class HotspotPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final HotspotPublisher publisher =
            new HotspotPublisher(kafkaTemplate, JsonMapper.builder().build());

    private final FirmsHotspot hotspot = new FirmsHotspot(
            "FIRMS-abc", "VIIRS_NOAA21_NRT", 43.10741, -4.19757, 316.59, 0.39, 0.36,
            "2026-03-21", "0219", "N21", "VIIRS", "n", "2.0NRT", 277.4, 1.54, "N",
            null, Instant.parse("2026-03-22T03:00:00Z"));

    @Test
    void publishesToTheRawTopicKeyedByDetectionId() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(hotspot);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.SATELLITE_HOTSPOTS), eq("FIRMS-abc"), message.capture());
        assertThat(message.getValue())
                .contains("\"id\":\"FIRMS-abc\"")
                .contains("\"latitude\":43.10741")
                .contains("\"acqTime\":\"0219\"")
                .contains("\"confidence\":\"n\"");
    }

    @Test
    void unconfirmedSendBecomesHotspotPublishingException() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> publisher.publish(hotspot))
                .isInstanceOf(HotspotPublishingException.class)
                .hasMessageContaining("FIRMS-abc");
    }

    @Test
    void synchronousSendFailureBecomesHotspotPublishingException() {
        // What KafkaTemplate does when the broker is down: send() itself throws.
        KafkaException failure = new KafkaException("Send failed");
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(failure);

        assertThatThrownBy(() -> publisher.publish(hotspot))
                .isInstanceOf(HotspotPublishingException.class)
                .hasCause(failure);
    }

    @Test
    void interruptionRestoresTheFlag() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> publisher.publish(hotspot))
                    .isInstanceOf(HotspotPublishingException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Clears the flag so that it does not reach the next test.
            Thread.interrupted();
        }
    }
}
