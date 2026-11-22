package com.emergencias.emergency_sensors.detection;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the detector counts each result in its own counter.
 */
class DetectionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DetectionMetrics metrics = new DetectionMetrics(registry);

    @Test
    void everyResultStartsAtZero() {
        assertThat(count(DetectionMetrics.ACCEPTED)).isZero();
        Arrays.stream(DiscardReason.values())
                .forEach(reason -> assertThat(count(reason.tag())).isZero());
    }

    @Test
    void countsAcceptedAndDiscardedHotspotsSeparately() {
        metrics.record(new Decision.Accepted(null));
        metrics.record(new Decision.Discarded(DiscardReason.STATIC_HEAT_SOURCE, "Mill"));
        metrics.record(new Decision.Discarded(DiscardReason.STATIC_HEAT_SOURCE, "Mill"));

        assertThat(count(DetectionMetrics.ACCEPTED)).isEqualTo(1);
        assertThat(count("static_heat_source")).isEqualTo(2);
        assertThat(count("outside_cantabria")).isZero();
    }

    private double count(String result) {
        return registry.get(DetectionMetrics.METRIC)
                .tag(DetectionMetrics.TAG, result)
                .counter()
                .count();
    }
}
