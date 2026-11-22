package com.emergencias.emergency_sensors.messaging.event;

import java.time.Instant;

/**
 * Event of the {@code emergency-events} topic, version 1 of the contract
 * defined by the core service ({@code IncidentEvent} in
 * {@code emergency-management}).
 * <p>
 * This repository keeps its own copy instead of sharing a library: the two
 * services are built and deployed separately and the contract is small. The
 * copy is pinned by {@code IncidentEventContractTest}, which fails if a field
 * is renamed, added or removed here.
 * <p>
 * The location is given by the zone code (INE code of the municipality), by
 * the coordinates or by both. Satellite detections always carry the
 * coordinates, and the zone code only when the point falls in a municipality.
 *
 * @param schemaVersion version of the contract, {@link #CURRENT_SCHEMA_VERSION}
 * @param id            event id; the core ignores an id it already knows
 * @param type          incident type, e.g. {@code FOREST_FIRE}
 * @param severity      0 to 4 (IGP level for forest fires)
 * @param zoneCode      INE code of the municipality, or {@code null}
 * @param latitude      WGS84 latitude
 * @param longitude     WGS84 longitude
 * @param occurredAt    when the incident was observed
 * @param source        origin of the event
 * @param description   text for the operators, in Spanish
 */
public record IncidentEvent(
        Integer schemaVersion,
        String id,
        String type,
        Integer severity,
        String zoneCode,
        Double latitude,
        Double longitude,
        Instant occurredAt,
        IncidentSource source,
        String description) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
