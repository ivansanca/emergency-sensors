package com.emergencias.emergency_sensors.detection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The parts of the municipal boundaries GeoJSON that the detector reads. Only
 * {@code MultiPolygon} geometries are expected: the file is generated from the
 * IGN API, which returns every municipality that way.
 * <p>
 * Coordinates follow the GeoJSON order: longitude first, then latitude.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GeoJson {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeatureCollection(List<Feature> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feature(FeatureProperties properties, Geometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeatureProperties(String ineCode, String name, boolean municipality) {
    }

    /**
     * {@code coordinates}: polygons, each a list of rings (the first is the
     * outer boundary, the rest are holes), each a list of [lon, lat] points.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(String type, List<List<List<List<Double>>>> coordinates) {
    }
}
