package com.emergencias.emergency_sensors.detection;

import com.emergencias.emergency_sensors.messaging.consumer.HotspotConsumer;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Wires the fire detector. It is on by default ({@code sensors.detector.enabled}):
 * unlike the ingestion, any number of instances can run it, because Kafka
 * shares the partitions among them.
 * <p>
 * The municipal boundaries are loaded at startup; if the file is missing or
 * broken, the service does not start.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DetectorProperties.class)
@ConditionalOnProperty(prefix = "sensors.detector", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DetectorConfig {

    @Bean
    MunicipalityLocator municipalityLocator(
            DetectorProperties properties, ResourceLoader resourceLoader, JsonMapper jsonMapper) {

        return MunicipalityLocator.load(
                resourceLoader.getResource(properties.municipalities()), jsonMapper);
    }

    @Bean
    HotspotEvaluator hotspotEvaluator(
            DetectorProperties properties, MunicipalityLocator municipalityLocator) {

        return new HotspotEvaluator(
                properties.acceptedConfidences(),
                properties.maxAge(),
                new StaticHeatSources(properties.staticHeatSources()),
                municipalityLocator);
    }

    @Bean
    DetectionMetrics detectionMetrics(MeterRegistry meterRegistry) {
        return new DetectionMetrics(meterRegistry);
    }

    @Bean
    HotspotConsumer hotspotConsumer(
            JsonMapper jsonMapper,
            HotspotEvaluator hotspotEvaluator,
            IncidentEventPublisher incidentEventPublisher,
            DetectionMetrics detectionMetrics) {

        return new HotspotConsumer(
                jsonMapper,
                hotspotEvaluator,
                incidentEventPublisher,
                detectionMetrics,
                Clock.systemUTC());
    }
}
