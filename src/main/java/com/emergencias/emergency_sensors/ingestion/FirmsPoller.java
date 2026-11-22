package com.emergencias.emergency_sensors.ingestion;

import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import com.emergencias.emergency_sensors.messaging.producer.HotspotPublisher;
import com.emergencias.emergency_sensors.messaging.producer.HotspotPublishingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.List;

/**
 * Queries every configured FIRMS source on a fixed delay and publishes the
 * hotspots that have not been sent yet to the raw hotspot topic.
 * <p>
 * It decides nothing about the hotspots: filtering is the detector's job. A
 * source that fails, for whatever reason, is logged and skipped so that the
 * others are still queried; its hotspots are picked up by the next query,
 * which covers the same days. If Kafka does not confirm a hotspot, it is not
 * marked as sent and goes out again next time. An interruption, which is how
 * the scheduler is stopped, ends the poll.
 */
@Slf4j
public class FirmsPoller {

    private final FirmsProperties properties;
    private final FirmsClient client;
    private final FirmsCsvParser parser;
    private final SentDetectionCache sentDetections;
    private final HotspotPublisher publisher;
    private final FirmsIngestionStatus status;
    private final Clock clock;

    public FirmsPoller(
            FirmsProperties properties,
            FirmsClient client,
            FirmsCsvParser parser,
            SentDetectionCache sentDetections,
            HotspotPublisher publisher,
            FirmsIngestionStatus status,
            Clock clock) {

        this.properties = properties;
        this.client = client;
        this.parser = parser;
        this.sentDetections = sentDetections;
        this.publisher = publisher;
        this.status = status;
        this.clock = clock;

        if (!properties.hasMapKey()) {
            log.warn("FIRMS_MAP_KEY no está configurada: no se consultará FIRMS");
        }
    }

    @Scheduled(
            initialDelayString = "${sensors.firms.initial-delay}",
            fixedDelayString = "${sensors.firms.poll-interval}")
    public void poll() {
        if (!properties.hasMapKey()) {
            return;
        }

        sentDetections.evictExpired();

        for (String dataset : properties.datasets()) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Consulta a FIRMS interrumpida: no se consultan más fuentes");
                return;
            }
            pollDataset(dataset);
        }
    }

    int pollDataset(String dataset) {
        try {
            List<FirmsHotspot> hotspots = parser.parse(
                    client.fetchCsv(dataset), dataset, clock.instant());

            int published = 0;
            for (FirmsHotspot hotspot : hotspots) {
                if (!sentDetections.alreadySent(hotspot.id())) {
                    publisher.publish(hotspot);
                    sentDetections.markSent(hotspot.id());
                    published++;
                }
            }

            status.recordSuccess(dataset, clock.instant());
            log.info("FIRMS {}: {} focos recibidos, {} nuevos publicados",
                    dataset, hotspots.size(), published);
            return published;

        } catch (FirmsApiException exception) {
            log.warn("{}", exception.getMessage());
        } catch (HotspotPublishingException exception) {
            log.error("FIRMS {}: no se pudieron publicar los focos ({})",
                    dataset, exception.getMessage());
        } catch (RuntimeException exception) {
            // Only the type: the message of an unknown exception may hold the
            // URL, and with it the MAP_KEY.
            log.error("FIRMS {}: error inesperado ({})",
                    dataset, exception.getClass().getName());
        }

        return 0;
    }
}
