package com.emergencias.emergency_sensors.detection;

import com.emergencias.emergency_sensors.detection.DetectorProperties.StaticHeatSource;

import java.util.List;
import java.util.Optional;

/**
 * Places that are hot all the time, such as steel mills, where the satellite
 * keeps detecting "fires" that are not.
 * <p>
 * FIRMS only labels these sources (type 2, "static land source") in the
 * standard processing data, weeks after the observation; the near real time
 * data that the service uses has no label. This list does the same job in
 * real time: a hotspot within the radius of a listed source is dropped.
 * <p>
 * The list is short, so every source is checked. Distances use the haversine
 * formula, which is far more precise than needed at a few hundred metres.
 */
public class StaticHeatSources {

    /** Mean Earth radius (IUGG). */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final List<StaticHeatSource> sources;

    public StaticHeatSources(List<StaticHeatSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public Optional<StaticHeatSource> near(double latitude, double longitude) {
        return sources.stream()
                .filter(source -> distanceMeters(
                        latitude, longitude, source.latitude(), source.longitude())
                        <= source.radiusMeters())
                .findFirst();
    }

    static double distanceMeters(
            double latitude1, double longitude1, double latitude2, double longitude2) {

        double deltaLatitude = Math.toRadians(latitude2 - latitude1);
        double deltaLongitude = Math.toRadians(longitude2 - longitude1);

        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(Math.toRadians(latitude1))
                * Math.cos(Math.toRadians(latitude2))
                * Math.pow(Math.sin(deltaLongitude / 2), 2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
