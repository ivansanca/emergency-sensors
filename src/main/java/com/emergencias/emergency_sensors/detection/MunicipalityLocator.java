package com.emergencias.emergency_sensors.detection;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds the municipality that contains a point, using the official municipal
 * boundaries of Cantabria.
 * <p>
 * The boundaries are the "recintos municipales" of the IGN (Base de Datos de
 * Límites Jurisdiccionales de España, CC BY 4.0), saved as GeoJSON in
 * {@code geo/cantabria-municipios.geojson}: the 102 municipalities plus the
 * Comunidad de Campoo y Cabuérniga. Using the real polygons instead of the
 * nearest capital matters: on the March 2026 sample the nearest capital gave
 * the wrong municipality for 71 of the 154 hotspots.
 * <p>
 * A point outside every polygon is outside Cantabria. That also covers
 * hotspots whose centre falls just across the border: a VIIRS pixel is 375 to
 * 800 m wide, so they may overlap Cantabria, but they are dropped and counted.
 * <p>
 * The check is done with JTS. With about a hundred areas a linear scan,
 * filtered first by each bounding box, is fast enough; the polygons are
 * "prepared" so that repeated point-in-polygon tests are cheap.
 */
public class MunicipalityLocator {

    private static final String MULTI_POLYGON = "MultiPolygon";

    private final GeometryFactory geometryFactory;
    private final List<Area> areas;

    private record Area(Municipality municipality, Envelope envelope, PreparedGeometry shape) {
    }

    private MunicipalityLocator(GeometryFactory geometryFactory, List<Area> areas) {
        this.geometryFactory = geometryFactory;
        this.areas = List.copyOf(areas);
    }

    /**
     * Reads the boundaries. Any problem stops the startup: without them the
     * detector cannot tell where a hotspot is.
     */
    public static MunicipalityLocator load(Resource boundaries, JsonMapper jsonMapper) {
        GeoJson.FeatureCollection collection;
        try (InputStream input = boundaries.getInputStream()) {
            collection = jsonMapper.readValue(input, GeoJson.FeatureCollection.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("No se pudieron leer los límites municipales de "
                    + boundaries.getDescription(), exception);
        }

        if (collection == null || collection.features() == null
                || collection.features().isEmpty()) {
            throw new IllegalStateException("No hay ningún municipio en "
                    + boundaries.getDescription());
        }

        GeometryFactory geometryFactory = new GeometryFactory();
        List<Area> areas = new ArrayList<>();
        for (GeoJson.Feature feature : collection.features()) {
            areas.add(toArea(feature, geometryFactory));
        }
        return new MunicipalityLocator(geometryFactory, areas);
    }

    public Optional<Municipality> locate(double latitude, double longitude) {
        Coordinate coordinate = new Coordinate(longitude, latitude);
        Point point = geometryFactory.createPoint(coordinate);

        return areas.stream()
                .filter(area -> area.envelope().covers(coordinate))
                // covers, not contains: a point exactly on a boundary line
                // still belongs to one of the two municipalities.
                .filter(area -> area.shape().covers(point))
                .map(Area::municipality)
                .findFirst();
    }

    public int size() {
        return areas.size();
    }

    private static Area toArea(GeoJson.Feature feature, GeometryFactory geometryFactory) {
        GeoJson.FeatureProperties properties = feature.properties();
        GeoJson.Geometry geometry = feature.geometry();

        if (properties == null || properties.ineCode() == null
                || geometry == null || geometry.coordinates() == null) {
            throw new IllegalStateException("Hay un municipio sin código o sin geometría");
        }
        if (!MULTI_POLYGON.equals(geometry.type())) {
            throw new IllegalStateException("Geometría no soportada en el municipio "
                    + properties.ineCode() + ": " + geometry.type());
        }

        MultiPolygon shape;
        try {
            shape = toMultiPolygon(geometry.coordinates(), geometryFactory);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Geometría no válida en el municipio "
                    + properties.ineCode(), exception);
        }

        return new Area(
                new Municipality(properties.ineCode(), properties.name(), properties.municipality()),
                shape.getEnvelopeInternal(),
                PreparedGeometryFactory.prepare(shape));
    }

    private static MultiPolygon toMultiPolygon(
            List<List<List<List<Double>>>> polygons, GeometryFactory geometryFactory) {

        Polygon[] result = new Polygon[polygons.size()];
        for (int i = 0; i < polygons.size(); i++) {
            List<List<List<Double>>> rings = polygons.get(i);

            LinearRing shell = toRing(rings.getFirst(), geometryFactory);
            LinearRing[] holes = new LinearRing[rings.size() - 1];
            for (int j = 1; j < rings.size(); j++) {
                holes[j - 1] = toRing(rings.get(j), geometryFactory);
            }
            result[i] = geometryFactory.createPolygon(shell, holes);
        }
        return geometryFactory.createMultiPolygon(result);
    }

    private static LinearRing toRing(List<List<Double>> points, GeometryFactory geometryFactory) {
        Coordinate[] coordinates = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            List<Double> point = points.get(i);
            coordinates[i] = new Coordinate(point.get(0), point.get(1));
        }
        return geometryFactory.createLinearRing(coordinates);
    }
}
