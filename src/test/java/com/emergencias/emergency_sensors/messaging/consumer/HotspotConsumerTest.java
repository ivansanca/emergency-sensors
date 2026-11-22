package com.emergencias.emergency_sensors.messaging.consumer;

import com.emergencias.emergency_sensors.detection.Decision;
import com.emergencias.emergency_sensors.detection.DetectionMetrics;
import com.emergencias.emergency_sensors.detection.DiscardReason;
import com.emergencias.emergency_sensors.detection.HotspotEvaluator;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import com.emergencias.emergency_sensors.messaging.event.IncidentEvent;
import com.emergencias.emergency_sensors.messaging.event.IncidentSource;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventPublisher;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventPublishingException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.LOS_TOJOS;
import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Checks what the detector does with each decision. The filters themselves
 * are tested in {@code HotspotEvaluatorTest}.
 */
class HotspotConsumerTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    private static final IncidentEvent EVENT = new IncidentEvent(
            1, "FIRMS-abc", "FOREST_FIRE", 0, "39086", 43.10741, -4.19757,
            Instant.parse("2026-11-22T03:02:00Z"), IncidentSource.FIRMS, "Foco");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HotspotEvaluator evaluator = mock(HotspotEvaluator.class);
    private final IncidentEventPublisher publisher = mock(IncidentEventPublisher.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final HotspotConsumer consumer = new HotspotConsumer(
            jsonMapper, evaluator, publisher, new DetectionMetrics(registry),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final String message = jsonMapper.writeValueAsString(
            at("FIRMS-abc", LOS_TOJOS, "n", Instant.parse("2026-11-22T03:02:00Z")));

    @Test
    void acceptedHotspotIsSentToTheCoreAndCounted() {
        when(evaluator.evaluate(any(), eq(NOW))).thenReturn(new Decision.Accepted(EVENT));

        consumer.consume(message);

        verify(publisher).publish(EVENT);
        assertThat(count("accepted")).isEqualTo(1);
    }

    @Test
    void hotspotIsReadFromTheMessage() {
        when(evaluator.evaluate(any(), eq(NOW)))
                .thenReturn(new Decision.Discarded(DiscardReason.TOO_OLD, null));

        consumer.consume(message);

        ArgumentCaptor<FirmsHotspot> hotspot = ArgumentCaptor.forClass(FirmsHotspot.class);
        verify(evaluator).evaluate(hotspot.capture(), eq(NOW));
        assertThat(hotspot.getValue().id()).isEqualTo("FIRMS-abc");
        assertThat(hotspot.getValue().latitude()).isEqualTo(LOS_TOJOS.latitude());
        assertThat(hotspot.getValue().acqTime()).isEqualTo("0302");
    }

    @Test
    void discardedHotspotIsOnlyCounted() {
        when(evaluator.evaluate(any(), eq(NOW)))
                .thenReturn(new Decision.Discarded(DiscardReason.STATIC_HEAT_SOURCE, "Mill"));

        consumer.consume(message);

        verifyNoInteractions(publisher);
        assertThat(count("static_heat_source")).isEqualTo(1);
    }

    @Test
    void unreadableMessageIsLeftToTheErrorHandler() {
        assertThatThrownBy(() -> consumer.consume("{not json"))
                .isInstanceOf(JacksonException.class);

        verifyNoInteractions(evaluator, publisher);
    }

    @Test
    void failedSendIsLeftToTheErrorHandlerAndNotCounted() {
        when(evaluator.evaluate(any(), eq(NOW))).thenReturn(new Decision.Accepted(EVENT));
        doThrow(new IncidentEventPublishingException("FIRMS-abc", new IllegalStateException()))
                .when(publisher).publish(EVENT);

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IncidentEventPublishingException.class);

        assertThat(count("accepted")).isZero();
    }

    private double count(String result) {
        return registry.get("sensors.detector.hotspots").tag("result", result).counter().count();
    }
}
