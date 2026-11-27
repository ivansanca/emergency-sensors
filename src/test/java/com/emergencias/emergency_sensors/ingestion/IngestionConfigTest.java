package com.emergencias.emergency_sensors.ingestion;

import com.emergencias.emergency_sensors.messaging.producer.HotspotPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Checks when the ingestion beans exist and that the properties bind.
 */
class IngestionConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(IngestionConfig.class)
            // SENSORS_INGESTION_ENABLED may be set on the developer's machine.
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(HotspotPublisher.class, () -> mock(HotspotPublisher.class))
            .withPropertyValues(
                    "sensors.firms.map-key=",
                    "sensors.firms.base-url=https://firms.example",
                    "sensors.firms.datasets=VIIRS_NOAA20_NRT,VIIRS_NOAA21_NRT",
                    "sensors.firms.area=-4.95,42.65,-3.05,43.60",
                    "sensors.firms.day-range=2",
                    "sensors.firms.initial-delay=PT1H",
                    "sensors.firms.poll-interval=PT15M",
                    "sensors.firms.connect-timeout=PT10S",
                    "sensors.firms.read-timeout=PT60S",
                    "sensors.firms.sent-cache-ttl=PT48H");

    @Test
    void ingestionIsOffByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(FirmsPoller.class));
    }

    @Test
    void applicationPropertiesKeepIngestionOff() throws IOException {
        // The integration tests force it off, so they cannot notice a change here.
        assertThat(PropertiesLoaderUtils
                .loadProperties(new ClassPathResource("application.properties"))
                .getProperty("sensors.ingestion.enabled"))
                .isEqualTo("false");
    }

    @Test
    void ingestionStartsWhenEnabled() {
        runner.withPropertyValues("sensors.ingestion.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FirmsPoller.class);

                    FirmsProperties properties = context.getBean(FirmsProperties.class);
                    assertThat(properties.datasets())
                            .containsExactly("VIIRS_NOAA20_NRT", "VIIRS_NOAA21_NRT");
                    assertThat(properties.dayRange()).isEqualTo(2);
                    assertThat(properties.hasMapKey()).isFalse();
                });
    }

    @Test
    void invalidDayRangeStopsTheStartup() {
        runner.withPropertyValues(
                        "sensors.ingestion.enabled=true",
                        "sensors.firms.day-range=10")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void withoutADateTheLastDaysAreQueried() {
        runner.withPropertyValues("sensors.ingestion.enabled=true", "sensors.firms.date=")
                .run(context -> assertThat(context.getBean(FirmsProperties.class).date())
                        .isNull());
    }

    @Test
    void dateIsReadAsAnIsoDate() {
        runner.withPropertyValues(
                        "sensors.ingestion.enabled=true",
                        "sensors.firms.date=2026-03-21")
                .run(context -> assertThat(context.getBean(FirmsProperties.class).date())
                        .isEqualTo(LocalDate.of(2026, 3, 21)));
    }

    @Test
    void invalidDateStopsTheStartupNamingTheProperty() {
        runner.withPropertyValues(
                        "sensors.ingestion.enabled=true",
                        "sensors.firms.date=21/03/2026")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("21/03/2026");
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("sensors.firms.date");
                });
    }

    @Test
    void replayProfileQueriesTheStandardQualityDataOfTheFires() throws IOException {
        Properties replay = PropertiesLoaderUtils
                .loadProperties(new ClassPathResource("application-replay.properties"));

        assertThat(replay.getProperty("sensors.ingestion.enabled")).isEqualTo("true");
        assertThat(replay.getProperty("sensors.firms.date")).isEqualTo("2026-03-21");
        assertThat(replay.getProperty("sensors.firms.day-range")).isEqualTo("2");
        assertThat(replay.getProperty("sensors.firms.datasets"))
                .isEqualTo("VIIRS_SNPP_SP,VIIRS_NOAA20_SP,VIIRS_NOAA21_SP");
        // Empty: no age limit, the hotspots are months old.
        assertThat(replay.getProperty("sensors.detector.max-age")).isEmpty();
        // The key only ever comes from FIRMS_MAP_KEY.
        assertThat(replay.stringPropertyNames()).doesNotContain("sensors.firms.map-key");
    }

    @Test
    void toStringHidesTheMapKey() {
        runner.withPropertyValues(
                        "sensors.ingestion.enabled=true",
                        "sensors.firms.map-key=secret-key")
                .run(context -> assertThat(context.getBean(FirmsProperties.class).toString())
                        .doesNotContain("secret-key")
                        .contains("mapKey=***"));
    }
}
