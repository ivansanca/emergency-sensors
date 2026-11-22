package com.emergencias.emergency_sensors.ingestion;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the gauge that shows the last successful query of each source.
 */
class FirmsIngestionStatusTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final FirmsIngestionStatus status =
            new FirmsIngestionStatus(registry, List.of("VIIRS_NOAA20_NRT"));

    @Test
    void gaugeIsZeroUntilTheFirstSuccess() {
        assertThat(gaugeValue()).isZero();
        assertThat(status.lastSuccess("VIIRS_NOAA20_NRT")).isEmpty();
    }

    @Test
    void gaugeShowsTheLastSuccessfulQuery() {
        Instant when = Instant.parse("2026-03-22T03:00:00Z");

        status.recordSuccess("VIIRS_NOAA20_NRT", when);

        assertThat(gaugeValue()).isEqualTo(when.getEpochSecond());
        assertThat(status.lastSuccess("VIIRS_NOAA20_NRT")).contains(when);
    }

    @Test
    void unknownSourceIsIgnored() {
        status.recordSuccess("MODIS_NRT", Instant.parse("2026-03-22T03:00:00Z"));

        assertThat(status.lastSuccess("MODIS_NRT")).isEmpty();
    }

    private double gaugeValue() {
        return registry.get(FirmsIngestionStatus.METRIC)
                .tag("dataset", "VIIRS_NOAA20_NRT")
                .gauge()
                .value();
    }
}
