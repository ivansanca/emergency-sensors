package com.emergencias.emergency_sensors.ingestion;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Time of the last successful query of each FIRMS source, exposed as the
 * {@code sensors.firms.last.success} gauge (epoch seconds, 0 = never).
 * <p>
 * An empty CSV looks the same whether there are no fires or the queries have
 * been failing for two days; this gauge tells the two apart.
 */
public class FirmsIngestionStatus {

    static final String METRIC = "sensors.firms.last.success";

    private final Map<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    public FirmsIngestionStatus(MeterRegistry registry, List<String> datasets) {
        for (String dataset : datasets) {
            AtomicLong epochSeconds = new AtomicLong();
            lastSuccess.put(dataset, epochSeconds);

            Gauge.builder(METRIC, epochSeconds, AtomicLong::doubleValue)
                    .description("Last successful FIRMS query (epoch seconds)")
                    .baseUnit("seconds")
                    .tag("dataset", dataset)
                    .register(registry);
        }
    }

    public void recordSuccess(String dataset, Instant when) {
        AtomicLong epochSeconds = lastSuccess.get(dataset);
        if (epochSeconds != null) {
            epochSeconds.set(when.getEpochSecond());
        }
    }

    public Optional<Instant> lastSuccess(String dataset) {
        AtomicLong epochSeconds = lastSuccess.get(dataset);
        return epochSeconds == null || epochSeconds.get() == 0
                ? Optional.empty()
                : Optional.of(Instant.ofEpochSecond(epochSeconds.get()));
    }
}
