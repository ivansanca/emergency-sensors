package com.emergencias.emergency_sensors.detection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the point-in-polygon search against the real IGN boundaries, and
 * that a broken boundaries file stops the startup.
 */
class MunicipalityLocatorTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static final MunicipalityLocator LOCATOR = MunicipalityLocator.load(
            new ClassPathResource("geo/cantabria-municipios.geojson"), JSON_MAPPER);

    /** A 10 x 10 degree square with a 2 x 2 hole in the middle. */
    private static final String SQUARE_WITH_HOLE = """
            {"type": "FeatureCollection", "features": [{"type": "Feature",
              "properties": {"ineCode": "00001", "name": "Square", "municipality": true},
              "geometry": {"type": "MultiPolygon", "coordinates": [[
                [[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]],
                [[4, 4], [6, 4], [6, 6], [4, 6], [4, 4]]
              ]]}}]}
            """;

    @Test
    void loadsTheMunicipalitiesAndCampooCabuerniga() {
        assertThat(LOCATOR.size()).isEqualTo(103);
    }

    @ParameterizedTest
    @CsvSource({
            "43.4623, -3.8099, 39075, Santander",
            "43.0016, -4.1378, 39059, Reinosa",
            "43.10741, -4.19757, 39086, Los Tojos",
            // The hotspot of 22/11/2026 by the steel mill is in Santander, not Camargo.
            "43.44348, -3.84716, 39075, Santander"
    })
    void findsTheMunicipalityOfAPoint(
            double latitude, double longitude, String ineCode, String name) {

        assertThat(LOCATOR.locate(latitude, longitude))
                .contains(new Municipality(ineCode, name, true));
    }

    @Test
    void campooCabuernigaIsFoundButHasNoZoneCode() {
        Municipality area = LOCATOR.locate(43.10016, -4.27623).orElseThrow();

        assertThat(area.ineCode()).isEqualTo("53072");
        assertThat(area.municipality()).isFalse();
        assertThat(area.zoneCode()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "43.4199, -4.7547",  // Llanes, Asturias
            "43.0784, -3.5539",  // Espinosa de los Monteros, Burgos
            "43.5500, -3.8000"   // Cantabrian Sea
    })
    void pointsOutsideCantabriaHaveNoMunicipality(double latitude, double longitude) {
        assertThat(LOCATOR.locate(latitude, longitude)).isEmpty();
    }

    @Test
    void holesAreNotPartOfTheArea() {
        MunicipalityLocator square = MunicipalityLocator.load(resource(SQUARE_WITH_HOLE), JSON_MAPPER);

        assertThat(square.locate(2, 2)).isPresent();
        assertThat(square.locate(5, 5)).isEmpty();
    }

    @Test
    void unreadableFileStopsTheStartup() {
        ByteArrayResource broken = resource("{not json");

        assertThatThrownBy(() -> MunicipalityLocator.load(broken, JSON_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudieron leer los límites municipales");
    }

    @Test
    void fileWithoutMunicipalitiesStopsTheStartup() {
        ByteArrayResource empty = resource("{\"type\": \"FeatureCollection\", \"features\": []}");

        assertThatThrownBy(() -> MunicipalityLocator.load(empty, JSON_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay ningún municipio");
    }

    @Test
    void otherGeometryTypesAreRejected() {
        ByteArrayResource polygon = resource(SQUARE_WITH_HOLE.replace("MultiPolygon", "Polygon"));

        assertThatThrownBy(() -> MunicipalityLocator.load(polygon, JSON_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Geometría no soportada en el municipio 00001");
    }

    @Test
    void openRingIsRejected() {
        ByteArrayResource open = resource(SQUARE_WITH_HOLE.replace("[0, 10], [0, 0]]", "[0, 10], [0, 1]]"));

        assertThatThrownBy(() -> MunicipalityLocator.load(open, JSON_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Geometría no válida en el municipio 00001");
    }

    @Test
    void featureWithoutCodeIsRejected() {
        ByteArrayResource withoutCode = resource(SQUARE_WITH_HOLE.replace("\"ineCode\": \"00001\", ", ""));

        assertThatThrownBy(() -> MunicipalityLocator.load(withoutCode, JSON_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin código o sin geometría");
    }

    private static ByteArrayResource resource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
    }
}
