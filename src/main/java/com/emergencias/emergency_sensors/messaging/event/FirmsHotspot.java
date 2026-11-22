package com.emergencias.emergency_sensors.messaging.event;

import java.time.Instant;

/**
 * One active fire pixel as NASA FIRMS reports it, published unchanged to the
 * raw hotspot topic so that it can be filtered, counted or reprocessed later.
 * <p>
 * The fields keep the meaning of the FIRMS CSV columns. Only two values are
 * normalised: {@code acqTime} always has four digits ({@code 0129}, not
 * {@code 129}) and {@code id} identifies the same detection across queries.
 * Optional columns are wrapper types so that a missing value stays
 * {@code null}.
 */
public record FirmsHotspot(
        String id,
        String dataset,
        Double latitude,
        Double longitude,
        Double brightTi4,
        Double scan,
        Double track,
        String acqDate,
        String acqTime,
        String satellite,
        String instrument,
        String confidence,
        String version,
        Double brightTi5,
        Double frp,
        String daynight,
        Integer type,
        Instant fetchedAt) {
}
