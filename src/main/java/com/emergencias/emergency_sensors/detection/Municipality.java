package com.emergencias.emergency_sensors.detection;

/**
 * Area of the IGN municipal boundaries that contains a hotspot.
 *
 * @param ineCode      INE code, the zone code the core service uses
 * @param name         official name
 * @param municipality {@code false} for the Comunidad de Campoo y Cabuérniga
 *                     (53072): land shared by several municipalities, with no
 *                     council of its own and no zone in the core's graph
 */
public record Municipality(String ineCode, String name, boolean municipality) {

    /**
     * Zone code to send to the core, or {@code null} when the area is not a
     * municipality. Without it the core falls back to the coordinates and
     * uses the nearest municipal capital.
     */
    public String zoneCode() {
        return municipality ? ineCode : null;
    }
}
