package com.emergencias.emergency_sensors.ingestion;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which detections were already published, so that the same hotspot
 * is not sent again on every query.
 * <p>
 * With a two-day range queried every 15 minutes, each hotspot comes back for
 * up to two days; without this cache a single detection would become well over
 * a hundred identical messages. An id is only remembered after Kafka has
 * confirmed it. The cache lives in memory: after a restart some hotspots are
 * sent again, which is harmless because the core ignores ids it already has.
 */
@RequiredArgsConstructor
public class SentDetectionCache {

    private final Map<String, Instant> expiries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public boolean alreadySent(String detectionId) {
        Instant expiry = expiries.get(detectionId);
        return expiry != null && expiry.isAfter(clock.instant());
    }

    public void markSent(String detectionId) {
        expiries.put(detectionId, clock.instant().plus(ttl));
    }

    public void evictExpired() {
        Instant now = clock.instant();
        expiries.values().removeIf(expiry -> !expiry.isAfter(now));
    }

    int size() {
        return expiries.size();
    }
}
