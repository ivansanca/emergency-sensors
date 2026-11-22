package com.emergencias.emergency_sensors.detection;

import com.emergencias.emergency_sensors.detection.DetectorProperties.StaticHeatSource;
import com.emergencias.emergency_sensors.messaging.consumer.HotspotConsumer;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Checks when the detector beans exist and that its properties bind and are
 * validated.
 */
class DetectorConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DetectorConfig.class)
            // SENSORS_DETECTOR_ENABLED may be set on the developer's machine.
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(IncidentEventPublisher.class, () -> mock(IncidentEventPublisher.class))
            .withPropertyValues(
                    "sensors.detector.accepted-confidences=n,h",
                    "sensors.detector.max-age=PT24H",
                    "sensors.detector.municipalities=classpath:geo/cantabria-municipios.geojson");

    @Test
    void detectorIsOnByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HotspotConsumer.class);
            assertThat(context.getBean(MunicipalityLocator.class).size()).isEqualTo(103);
            assertThat(context.getBean(DetectorProperties.class).maxAge())
                    .isEqualTo(Duration.ofHours(24));
        });
    }

    @Test
    void detectorCanBeTurnedOff() {
        runner.withPropertyValues("sensors.detector.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(HotspotConsumer.class));
    }

    @Test
    void staticHeatSourcesBind() {
        runner.withPropertyValues(
                        "sensors.detector.static-heat-sources[0].name=Mill",
                        "sensors.detector.static-heat-sources[0].latitude=43.44348",
                        "sensors.detector.static-heat-sources[0].longitude=-3.84716",
                        "sensors.detector.static-heat-sources[0].radius-meters=1000")
                .run(context -> assertThat(context.getBean(DetectorProperties.class).staticHeatSources())
                        .containsExactly(new StaticHeatSource("Mill", 43.44348, -3.84716, 1000)));
    }

    @Test
    void withoutStaticHeatSourcesTheListIsEmpty() {
        runner.run(context -> assertThat(
                context.getBean(DetectorProperties.class).staticHeatSources()).isEmpty());
    }

    @Test
    void emptyMaximumAgeMeansNoLimit() {
        runner.withPropertyValues("sensors.detector.max-age=")
                .run(context -> assertThat(context.getBean(DetectorProperties.class).maxAge()).isNull());
    }

    @Test
    void invalidRadiusStopsTheStartup() {
        runner.withPropertyValues(
                        "sensors.detector.static-heat-sources[0].name=Mill",
                        "sensors.detector.static-heat-sources[0].latitude=43.44348",
                        "sensors.detector.static-heat-sources[0].longitude=-3.84716",
                        "sensors.detector.static-heat-sources[0].radius-meters=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingBoundariesFileStopsTheStartup() {
        runner.withPropertyValues("sensors.detector.municipalities=classpath:geo/missing.geojson")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void applicationPropertiesKeepTheDetectorOnAndDecodeTheSourceName() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application.properties"));

        assertThat(properties.getProperty("sensors.detector.enabled")).isEqualTo("true");
        // The file is read as ISO-8859-1, so the accents are written as unicode escapes.
        assertThat(properties.getProperty("sensors.detector.static-heat-sources[0].name"))
                .isEqualTo("Acería de Nueva Montaña (Santander)");
    }
}
