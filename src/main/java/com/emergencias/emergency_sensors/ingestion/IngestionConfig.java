package com.emergencias.emergency_sensors.ingestion;

import com.emergencias.emergency_sensors.messaging.producer.HotspotPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Wires the FIRMS ingestion. It only exists when
 * {@code sensors.ingestion.enabled=true}.
 * <p>
 * Ingestion is off by default because only one instance may query FIRMS:
 * several pollers would download the same data and share the same request
 * limit. When the service is scaled, a single instance turns it on and the
 * rest only run the detector.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(FirmsProperties.class)
@ConditionalOnProperty(prefix = "sensors.ingestion", name = "enabled", havingValue = "true")
public class IngestionConfig {

    @Bean
    FirmsClient firmsClient(RestClient.Builder restClientBuilder, FirmsProperties properties) {
        return new FirmsClient(restClientBuilder, properties);
    }

    @Bean
    FirmsCsvParser firmsCsvParser() {
        return new FirmsCsvParser();
    }

    @Bean
    SentDetectionCache sentDetectionCache(FirmsProperties properties) {
        return new SentDetectionCache(properties.sentCacheTtl(), Clock.systemUTC());
    }

    @Bean
    FirmsIngestionStatus firmsIngestionStatus(
            MeterRegistry meterRegistry, FirmsProperties properties) {
        return new FirmsIngestionStatus(meterRegistry, properties.datasets());
    }

    @Bean
    FirmsPoller firmsPoller(
            FirmsProperties properties,
            FirmsClient firmsClient,
            FirmsCsvParser firmsCsvParser,
            SentDetectionCache sentDetectionCache,
            HotspotPublisher hotspotPublisher,
            FirmsIngestionStatus firmsIngestionStatus) {

        return new FirmsPoller(
                properties,
                firmsClient,
                firmsCsvParser,
                sentDetectionCache,
                hotspotPublisher,
                firmsIngestionStatus,
                Clock.systemUTC());
    }
}
