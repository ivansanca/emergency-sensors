package com.emergencias.emergency_sensors.ingestion;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks when a sent detection is remembered and when it is forgotten.
 */
class SentDetectionCacheTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-03-21T12:00:00Z"));
    private final SentDetectionCache cache = new SentDetectionCache(Duration.ofHours(48), clock);

    @Test
    void unknownDetectionIsNotSent() {
        assertThat(cache.alreadySent("FIRMS-a")).isFalse();
    }

    @Test
    void markedDetectionIsSentUntilItExpires() {
        cache.markSent("FIRMS-a");
        assertThat(cache.alreadySent("FIRMS-a")).isTrue();

        clock.advance(Duration.ofHours(47));
        assertThat(cache.alreadySent("FIRMS-a")).isTrue();

        clock.advance(Duration.ofHours(1));
        assertThat(cache.alreadySent("FIRMS-a")).isFalse();
    }

    @Test
    void evictExpiredRemovesOnlyExpiredEntries() {
        cache.markSent("FIRMS-old");
        clock.advance(Duration.ofHours(24));
        cache.markSent("FIRMS-new");
        clock.advance(Duration.ofHours(25));

        cache.evictExpired();

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.alreadySent("FIRMS-new")).isTrue();
        assertThat(cache.alreadySent("FIRMS-old")).isFalse();
    }
}
