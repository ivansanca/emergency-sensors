package com.emergencias.emergency_sensors.messaging.event;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Hotspots and places shared by the detector tests. The expected
 * municipality of each place was checked against the IGN boundaries.
 */
public final class TestHotspots {

    /** Forest in Los Tojos (39086): a real hotspot of 21 March 2026. */
    public static final Place LOS_TOJOS = new Place(43.10741, -4.19757);

    /** Hotspot of 22 November 2026 next to the Nueva Montaña steel mill (Santander). */
    public static final Place STEEL_MILL = new Place(43.44348, -3.84716);

    /** Comunidad de Campoo y Cabuérniga (53072), about 2.8 km from its border. */
    public static final Place CAMPOO_CABUERNIGA = new Place(43.10016, -4.27623);

    /** Llanes, Asturias: inside the query rectangle, outside Cantabria. */
    public static final Place LLANES = new Place(43.4199, -4.7547);

    public record Place(double latitude, double longitude) {
    }

    private TestHotspots() {
    }

    /**
     * A VIIRS NOAA-21 hotspot observed at {@code observedAt}, truncated to the
     * minute as FIRMS does.
     */
    public static FirmsHotspot at(String id, Place place, String confidence, Instant observedAt) {
        ZonedDateTime time = observedAt.atZone(ZoneOffset.UTC);

        return new FirmsHotspot(
                id, "VIIRS_NOAA21_NRT", place.latitude(), place.longitude(),
                316.59, 0.39, 0.36,
                time.toLocalDate().toString(),
                String.format(Locale.ROOT, "%02d%02d", time.getHour(), time.getMinute()),
                "N21", "VIIRS", confidence, "2.0NRT", 277.4, 1.54, "N", null,
                observedAt.plusSeconds(3600));
    }
}
