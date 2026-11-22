package com.emergencias.emergency_sensors.detection;

import java.util.Locale;

/**
 * Why a hotspot was not sent to the core, in the order the filters run.
 */
public enum DiscardReason {

    /**
     * Low confidence: according to FIRMS, mostly daytime pixels affected by
     * sun glint or with a weak temperature anomaly.
     */
    LOW_CONFIDENCE,

    /** Observed longer ago than {@code sensors.detector.max-age}. */
    TOO_OLD,

    /** Next to a place that is always hot, such as a steel mill. */
    STATIC_HEAT_SOURCE,

    /** Outside every municipality of Cantabria, border areas included. */
    OUTSIDE_CANTABRIA;

    /** Value of the metric tag, e.g. {@code static_heat_source}. */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
