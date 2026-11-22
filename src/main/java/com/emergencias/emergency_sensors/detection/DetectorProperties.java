package com.emergencias.emergency_sensors.detection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Settings of the fire detector ({@code sensors.detector.*}).
 *
 * @param acceptedConfidences VIIRS confidence values that pass the filter
 *                            ({@code l} low, {@code n} nominal, {@code h} high)
 * @param maxAge              hotspots observed longer ago are dropped;
 *                            empty means no limit, for replaying old data
 * @param municipalities      location of the GeoJSON file with the municipal
 *                            boundaries, e.g. {@code classpath:geo/...}
 * @param staticHeatSources   places that are always hot; may be empty
 */
@Validated
@ConfigurationProperties(prefix = "sensors.detector")
public record DetectorProperties(
        @NotEmpty List<@NotBlank String> acceptedConfidences,
        Duration maxAge,
        @NotBlank String municipalities,
        List<@Valid StaticHeatSource> staticHeatSources) {

    public DetectorProperties {
        staticHeatSources = staticHeatSources == null ? List.of() : List.copyOf(staticHeatSources);
    }

    /**
     * A place that is always hot. Hotspots within {@code radiusMeters} of it
     * are not fires.
     *
     * @param name         shown in the logs when a hotspot is dropped
     * @param latitude     WGS84 latitude of the centre
     * @param longitude    WGS84 longitude of the centre
     * @param radiusMeters radius around the centre
     */
    public record StaticHeatSource(
            @NotBlank String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @Positive int radiusMeters) {
    }
}
