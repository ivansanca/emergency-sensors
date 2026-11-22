package com.emergencias.emergency_sensors.detection;

import com.emergencias.emergency_sensors.detection.DetectorProperties.StaticHeatSource;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import com.emergencias.emergency_sensors.messaging.event.IncidentEvent;
import com.emergencias.emergency_sensors.messaging.event.IncidentSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.CAMPOO_CABUERNIGA;
import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.LLANES;
import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.LOS_TOJOS;
import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.STEEL_MILL;
import static com.emergencias.emergency_sensors.messaging.event.TestHotspots.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks each filter of the detector, the order in which they run and the
 * event built for an accepted hotspot.
 */
class HotspotEvaluatorTest {

    private static final MunicipalityLocator LOCATOR = MunicipalityLocator.load(
            new ClassPathResource("geo/cantabria-municipios.geojson"),
            JsonMapper.builder().build());

    private static final StaticHeatSource MILL = new StaticHeatSource(
            "Mill", STEEL_MILL.latitude(), STEEL_MILL.longitude(), 1000);

    private static final Instant OBSERVED = Instant.parse("2026-11-22T03:02:00Z");
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    private final HotspotEvaluator evaluator = evaluator(Duration.ofHours(24));

    @Test
    void forestHotspotBecomesAForestFireAtIgp0() {
        IncidentEvent event = accepted(evaluator.evaluate(at("FIRMS-1", LOS_TOJOS, "n", OBSERVED), NOW));

        assertThat(event.schemaVersion()).isEqualTo(IncidentEvent.CURRENT_SCHEMA_VERSION);
        assertThat(event.id()).isEqualTo("FIRMS-1");
        assertThat(event.type()).isEqualTo("FOREST_FIRE");
        assertThat(event.severity()).isZero();
        assertThat(event.zoneCode()).isEqualTo("39086");
        assertThat(event.latitude()).isEqualTo(LOS_TOJOS.latitude());
        assertThat(event.longitude()).isEqualTo(LOS_TOJOS.longitude());
        assertThat(event.occurredAt()).isEqualTo(OBSERVED);
        assertThat(event.source()).isEqualTo(IncidentSource.FIRMS);
        assertThat(event.description()).isEqualTo(
                "Foco detectado por satélite en Los Tojos: VIIRS (NOAA-21), "
                        + "confianza nominal, potencia radiativa 1,5 MW.");
    }

    @Test
    void highConfidenceIsAcceptedWhateverTheCase() {
        assertThat(evaluator.evaluate(at("FIRMS-1", LOS_TOJOS, "H", OBSERVED), NOW))
                .isInstanceOf(Decision.Accepted.class);
    }

    @Test
    void lowConfidenceIsDropped() {
        assertThat(evaluator.evaluate(at("FIRMS-1", LOS_TOJOS, "l", OBSERVED), NOW))
                .isEqualTo(new Decision.Discarded(DiscardReason.LOW_CONFIDENCE, "l"));
    }

    @Test
    void confidenceIsCheckedBeforeTheLocation() {
        assertThat(evaluator.evaluate(at("FIRMS-1", LLANES, "l", OBSERVED), NOW))
                .isEqualTo(new Decision.Discarded(DiscardReason.LOW_CONFIDENCE, "l"));
    }

    @Test
    void hotspotOlderThanTheMaximumAgeIsDropped() {
        Instant later = OBSERVED.plus(Duration.ofHours(25));

        assertThat(evaluator.evaluate(at("FIRMS-1", LOS_TOJOS, "n", OBSERVED), later))
                .isEqualTo(new Decision.Discarded(DiscardReason.TOO_OLD, OBSERVED.toString()));
    }

    @Test
    void hotspotWithinTheMaximumAgeIsAccepted() {
        Instant later = OBSERVED.plus(Duration.ofHours(23));

        assertThat(evaluator.evaluate(at("FIRMS-1", LOS_TOJOS, "n", OBSERVED), later))
                .isInstanceOf(Decision.Accepted.class);
    }

    @Test
    void withoutMaximumAgeOldHotspotsAreAccepted() {
        Instant muchLater = OBSERVED.plus(Duration.ofDays(400));

        assertThat(evaluator(null).evaluate(at("FIRMS-1", LOS_TOJOS, "n", OBSERVED), muchLater))
                .isInstanceOf(Decision.Accepted.class);
    }

    @Test
    void hotspotAtAStaticHeatSourceIsDropped() {
        assertThat(evaluator.evaluate(at("FIRMS-1", STEEL_MILL, "n", OBSERVED), NOW))
                .isEqualTo(new Decision.Discarded(DiscardReason.STATIC_HEAT_SOURCE, "Mill"));
    }

    @Test
    void hotspotOutsideCantabriaIsDropped() {
        assertThat(evaluator.evaluate(at("FIRMS-1", LLANES, "n", OBSERVED), NOW))
                .isEqualTo(new Decision.Discarded(DiscardReason.OUTSIDE_CANTABRIA, null));
    }

    @Test
    void hotspotInCampooCabuernigaIsSentWithCoordinatesOnly() {
        IncidentEvent event = accepted(
                evaluator.evaluate(at("FIRMS-1", CAMPOO_CABUERNIGA, "n", OBSERVED), NOW));

        assertThat(event.zoneCode()).isNull();
        assertThat(event.latitude()).isEqualTo(CAMPOO_CABUERNIGA.latitude());
        assertThat(event.description()).contains("en Comunidad de Campoo y Cabuérniga:");
    }

    @Test
    void hotspotWithoutPositionIsInvalid() {
        FirmsHotspot withoutLatitude = new FirmsHotspot(
                "FIRMS-1", "VIIRS_NOAA21_NRT", null, -4.19757, null, null, null,
                "2026-11-22", "0302", "N21", "VIIRS", "n", null, null, null, "N", null, null);

        assertThatThrownBy(() -> evaluator.evaluate(withoutLatitude, NOW))
                .isInstanceOf(InvalidHotspotException.class)
                .hasMessageContaining("FIRMS-1");
    }

    @Test
    void hotspotWithoutIdIsInvalid() {
        assertThatThrownBy(() -> evaluator.evaluate(at(" ", LOS_TOJOS, "n", OBSERVED), NOW))
                .isInstanceOf(InvalidHotspotException.class)
                .hasMessage("El foco no tiene identificador");
    }

    @Test
    void nullHotspotIsInvalid() {
        assertThatThrownBy(() -> evaluator.evaluate(null, NOW))
                .isInstanceOf(InvalidHotspotException.class);
    }

    @Test
    void impossibleTimeIsInvalid() {
        FirmsHotspot badTime = new FirmsHotspot(
                "FIRMS-1", "VIIRS_NOAA21_NRT", LOS_TOJOS.latitude(), LOS_TOJOS.longitude(),
                null, null, null, "2026-11-22", "2561", "N21", "VIIRS", "n",
                null, null, null, "N", null, null);

        assertThatThrownBy(() -> evaluator.evaluate(badTime, NOW))
                .isInstanceOf(InvalidHotspotException.class)
                .hasCauseInstanceOf(DateTimeException.class);
    }

    @Test
    void descriptionCopesWithMissingOptionalFields() {
        FirmsHotspot sparse = new FirmsHotspot(
                "FIRMS-1", "VIIRS_NOAA21_NRT", 43.1, -4.2, null, null, null,
                "2026-11-22", "0302", null, null, "x", null, null, null, "N", null, null);

        assertThat(HotspotEvaluator.description(sparse, new Municipality("39075", "Santander", true)))
                .isEqualTo("Foco detectado por satélite en Santander: satélite desconocido, confianza x.");
    }

    @Test
    void descriptionNamesEachSatellite() {
        Municipality santander = new Municipality("39075", "Santander", true);

        assertThat(HotspotEvaluator.description(withSatellite("N"), santander)).contains("VIIRS (Suomi NPP)");
        assertThat(HotspotEvaluator.description(withSatellite("N20"), santander)).contains("VIIRS (NOAA-20)");
        assertThat(HotspotEvaluator.description(withSatellite("X9"), santander)).contains("VIIRS (X9)");
        assertThat(HotspotEvaluator.description(withConfidence("l"), santander)).contains("confianza baja");
        assertThat(HotspotEvaluator.description(withConfidence("h"), santander)).contains("confianza alta");
    }

    private static HotspotEvaluator evaluator(Duration maxAge) {
        return new HotspotEvaluator(
                List.of("n", "h"), maxAge, new StaticHeatSources(List.of(MILL)), LOCATOR);
    }

    private static IncidentEvent accepted(Decision decision) {
        assertThat(decision).isInstanceOf(Decision.Accepted.class);
        return ((Decision.Accepted) decision).event();
    }

    private static FirmsHotspot withSatellite(String satellite) {
        return new FirmsHotspot(
                "FIRMS-1", "VIIRS_NOAA21_NRT", 43.1, -4.2, null, null, null,
                "2026-11-22", "0302", satellite, "VIIRS", "n", null, null, null, "N", null, null);
    }

    private static FirmsHotspot withConfidence(String confidence) {
        return new FirmsHotspot(
                "FIRMS-1", "VIIRS_NOAA21_NRT", 43.1, -4.2, null, null, null,
                "2026-11-22", "0302", "N21", "VIIRS", confidence, null, null, null, "N", null, null);
    }
}
