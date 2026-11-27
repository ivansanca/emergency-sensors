package com.emergencias.emergency_sensors.ingestion;

import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import com.emergencias.emergency_sensors.messaging.producer.HotspotPublisher;
import com.emergencias.emergency_sensors.messaging.producer.HotspotPublishingException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Checks what the poller publishes and remembers, and that a failure stays
 * within the source where it happened.
 */
class FirmsPollerTest {

    private static final String NOAA20 = "VIIRS_NOAA20_NRT";
    private static final String NOAA21 = "VIIRS_NOAA21_NRT";
    private static final Instant NOW = Instant.parse("2026-03-22T03:00:00Z");

    private static final String TWO_HOTSPOTS = FirmsCsvParserTest.NRT_HEADER + "\n"
            + "43.10741,-4.19757,316.59,0.39,0.36,2026-03-21,219,N21,VIIRS,n,2.0NRT,277.4,1.54,N\n"
            + "43.14463,-3.85501,308.73,0.33,0.55,2026-03-21,129,N21,VIIRS,n,2.0NRT,263.77,1.37,N\n";

    private final FirmsClient client = mock(FirmsClient.class);
    private final HotspotPublisher publisher = mock(HotspotPublisher.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final SentDetectionCache cache = new SentDetectionCache(Duration.ofHours(48), clock);

    @Test
    void publishesEachDetectionOnlyOnce() {
        when(client.fetchCsv(NOAA21)).thenReturn(TWO_HOTSPOTS);
        FirmsPoller poller = poller(properties("key", List.of(NOAA21)), status(NOAA21));

        poller.poll();
        poller.poll();

        verify(publisher, times(2)).publish(any());
    }

    @Test
    void publishesTheParsedHotspots() {
        when(client.fetchCsv(NOAA21)).thenReturn(TWO_HOTSPOTS);
        FirmsPoller poller = poller(properties("key", List.of(NOAA21)), status(NOAA21));

        assertThat(poller.pollDataset(NOAA21)).isEqualTo(2);

        ArgumentCaptor<FirmsHotspot> captor = ArgumentCaptor.forClass(FirmsHotspot.class);
        verify(publisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FirmsHotspot::dataset, FirmsHotspot::fetchedAt)
                .containsOnly(tuple(NOAA21, NOW));
    }

    @Test
    void doesNothingWithoutMapKey() {
        FirmsPoller poller = poller(properties("", List.of(NOAA21)), status(NOAA21));

        poller.poll();

        verifyNoInteractions(client, publisher);
    }

    @Test
    void failingSourceDoesNotStopTheOthers() {
        when(client.fetchCsv(NOAA20)).thenThrow(new FirmsApiException("FIRMS respondió 500"));
        when(client.fetchCsv(NOAA21)).thenReturn(TWO_HOTSPOTS);
        FirmsIngestionStatus status = status(NOAA20, NOAA21);
        FirmsPoller poller = poller(properties("key", List.of(NOAA20, NOAA21)), status);

        poller.poll();

        verify(publisher, times(2)).publish(any());
        assertThat(status.lastSuccess(NOAA20)).isEmpty();
        assertThat(status.lastSuccess(NOAA21)).contains(NOW);
    }

    @Test
    void unexpectedFailureInTheFirstSourceDoesNotStopTheSecond() {
        when(client.fetchCsv(NOAA20)).thenReturn(TWO_HOTSPOTS);
        when(client.fetchCsv(NOAA21)).thenReturn(TWO_HOTSPOTS);
        doThrow(new IllegalStateException("unexpected"))
                .doNothing()
                .when(publisher).publish(any());
        FirmsIngestionStatus status = status(NOAA20, NOAA21);
        FirmsPoller poller = poller(properties("key", List.of(NOAA20, NOAA21)), status);

        poller.poll();

        // The failed attempt plus both hotspots from the second source: the one
        // that failed was not marked as sent.
        verify(publisher, times(3)).publish(any());
        assertThat(status.lastSuccess(NOAA20)).isEmpty();
        assertThat(status.lastSuccess(NOAA21)).contains(NOW);
    }

    @Test
    void interruptedPollDoesNotQueryTheRemainingSources() {
        when(client.fetchCsv(NOAA20)).thenReturn(TWO_HOTSPOTS);
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new HotspotPublishingException("FIRMS-x", new InterruptedException());
        }).when(publisher).publish(any());
        FirmsPoller poller = poller(
                properties("key", List.of(NOAA20, NOAA21)), status(NOAA20, NOAA21));

        try {
            poller.poll();

            verify(client, never()).fetchCsv(NOAA21);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Clears the flag so that it does not reach the next test.
            Thread.interrupted();
        }
    }

    @Test
    void pollEvictsExpiredDetections() {
        when(client.fetchCsv(NOAA21)).thenReturn(FirmsCsvParserTest.NRT_HEADER);
        FirmsPoller poller = poller(properties("key", List.of(NOAA21)), status(NOAA21));
        cache.markSent("FIRMS-old");
        clock.advance(Duration.ofHours(49));

        poller.poll();

        assertThat(cache.size()).isZero();
    }

    @Test
    void hotspotNotConfirmedByKafkaIsSentAgainNextTime() {
        when(client.fetchCsv(NOAA21)).thenReturn(TWO_HOTSPOTS);
        doThrow(new HotspotPublishingException("FIRMS-x", new RuntimeException("down")))
                .doNothing()
                .when(publisher).publish(any());
        FirmsIngestionStatus status = status(NOAA21);
        FirmsPoller poller = poller(properties("key", List.of(NOAA21)), status);

        assertThat(poller.pollDataset(NOAA21)).isZero();
        assertThat(status.lastSuccess(NOAA21)).isEmpty();

        assertThat(poller.pollDataset(NOAA21)).isEqualTo(2);
        verify(publisher, times(3)).publish(any());
    }

    @Test
    void expiredDetectionsAreForgotten() {
        when(client.fetchCsv(NOAA21)).thenReturn(TWO_HOTSPOTS);
        doNothing().when(publisher).publish(any());
        FirmsPoller poller = poller(properties("key", List.of(NOAA21)), status(NOAA21));

        poller.poll();
        clock.advance(Duration.ofHours(49));
        poller.poll();

        verify(publisher, times(4)).publish(any());
    }

    private FirmsPoller poller(FirmsProperties properties, FirmsIngestionStatus status) {
        return new FirmsPoller(
                properties, client, new FirmsCsvParser(), cache, publisher, status, clock);
    }

    private static FirmsIngestionStatus status(String... datasets) {
        return new FirmsIngestionStatus(new SimpleMeterRegistry(), List.of(datasets));
    }

    private static FirmsProperties properties(String mapKey, List<String> datasets) {
        return new FirmsProperties(
                mapKey,
                "https://firms.example",
                datasets,
                "-4.95,42.65,-3.05,43.60",
                2,
                null,
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                Duration.ofHours(48));
    }
}
