package com.emergencias.emergency_sensors.detection;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Counts the hotspots evaluated by the detector, by result, in the
 * {@code sensors.detector.hotspots} counter: {@code accepted} or the reason
 * they were dropped ({@code low_confidence}, {@code too_old},
 * {@code static_heat_source}, {@code outside_cantabria}).
 * <p>
 * Every counter is registered at startup, so a reason that never happened
 * shows 0 instead of being missing. These figures say how much each filter
 * removes, which is what has to be checked when tuning them.
 */
public class DetectionMetrics {

    static final String METRIC = "sensors.detector.hotspots";
    static final String TAG = "result";
    static final String ACCEPTED = "accepted";

    private final Counter acceptedCounter;
    private final Map<DiscardReason, Counter> discarded = new EnumMap<>(DiscardReason.class);

    public DetectionMetrics(MeterRegistry registry) {
        acceptedCounter = counter(registry, ACCEPTED);
        for (DiscardReason reason : DiscardReason.values()) {
            discarded.put(reason, counter(registry, reason.tag()));
        }
    }

    public void record(Decision decision) {
        if (decision instanceof Decision.Discarded discard) {
            discarded.get(discard.reason()).increment();
        } else {
            acceptedCounter.increment();
        }
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder(METRIC)
                .description("Hotspots evaluated by the fire detector")
                .tag(TAG, result)
                .register(registry);
    }
}
