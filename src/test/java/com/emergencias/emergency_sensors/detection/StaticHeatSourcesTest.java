package com.emergencias.emergency_sensors.detection;

import com.emergencias.emergency_sensors.detection.DetectorProperties.StaticHeatSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Checks the distance formula and the radius around each heat source.
 */
class StaticHeatSourcesTest {

    /** Length of one degree of latitude on the mean Earth sphere. */
    private static final double METERS_PER_DEGREE_OF_LATITUDE = 111_195.08;

    private static final StaticHeatSource MILL =
            new StaticHeatSource("Mill", 43.44348, -3.84716, 1000);

    private final StaticHeatSources sources = new StaticHeatSources(List.of(MILL));

    @Test
    void oneDegreeOfLatitudeIsAbout111Kilometres() {
        assertThat(StaticHeatSources.distanceMeters(43, -3.8, 44, -3.8))
                .isCloseTo(METERS_PER_DEGREE_OF_LATITUDE, within(1.0));
    }

    @Test
    void samePointIsAtZeroMetres() {
        assertThat(StaticHeatSources.distanceMeters(43.44348, -3.84716, 43.44348, -3.84716))
                .isZero();
    }

    @Test
    void hotspotWithinTheRadiusIsNearTheSource() {
        assertThat(sources.near(northOfTheMill(500), MILL.longitude())).contains(MILL);
    }

    @Test
    void hotspotBeyondTheRadiusIsNotNearTheSource() {
        assertThat(sources.near(northOfTheMill(1500), MILL.longitude())).isEmpty();
    }

    @Test
    void withoutSourcesNothingIsNear() {
        assertThat(new StaticHeatSources(List.of()).near(MILL.latitude(), MILL.longitude()))
                .isEmpty();
    }

    private static double northOfTheMill(double meters) {
        return MILL.latitude() + meters / METERS_PER_DEGREE_OF_LATITUDE;
    }
}
