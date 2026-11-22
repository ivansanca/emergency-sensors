package com.emergencias.emergency_sensors.detection;

import com.emergencias.emergency_sensors.detection.DetectorProperties.StaticHeatSource;
import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import com.emergencias.emergency_sensors.messaging.event.IncidentEvent;
import com.emergencias.emergency_sensors.messaging.event.IncidentSource;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a satellite hotspot is a fire to report and, if so, builds
 * the event for the core service.
 * <p>
 * The filters run from the cheapest to the most expensive: confidence, age,
 * static heat sources and finally the municipality, the only one that needs
 * the polygons. A hotspot that passes all of them becomes a forest fire with
 * severity 0 (IGP 0): a satellite cannot judge how serious a fire is, so the
 * event only raises the alert and the operators set the level.
 * <p>
 * The event keeps the hotspot id, so a hotspot processed twice (after a
 * restart or a retry) produces the same event and the core ignores the copy.
 * <p>
 * This class has no state and does not depend on Spring or Kafka, so it can be
 * tested with plain values.
 */
public class HotspotEvaluator {

    static final String INCIDENT_TYPE = "FOREST_FIRE";
    static final int SATELLITE_SEVERITY = 0;

    private static final DateTimeFormatter ACQUISITION_TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final Locale SPANISH = Locale.of("es", "ES");

    private final Set<String> acceptedConfidences;
    private final Duration maxAge;
    private final StaticHeatSources staticHeatSources;
    private final MunicipalityLocator municipalities;

    /**
     * @param maxAge {@code null} for no limit
     */
    public HotspotEvaluator(
            List<String> acceptedConfidences,
            Duration maxAge,
            StaticHeatSources staticHeatSources,
            MunicipalityLocator municipalities) {

        this.acceptedConfidences = acceptedConfidences.stream()
                .map(HotspotEvaluator::normalized)
                .collect(Collectors.toUnmodifiableSet());
        this.maxAge = maxAge;
        this.staticHeatSources = staticHeatSources;
        this.municipalities = municipalities;
    }

    public Decision evaluate(FirmsHotspot hotspot, Instant now) {
        requireFields(hotspot);
        Instant observedAt = observedAt(hotspot);
        double latitude = hotspot.latitude();
        double longitude = hotspot.longitude();

        if (!acceptedConfidences.contains(normalized(hotspot.confidence()))) {
            return new Decision.Discarded(DiscardReason.LOW_CONFIDENCE, hotspot.confidence());
        }

        if (maxAge != null && observedAt.plus(maxAge).isBefore(now)) {
            return new Decision.Discarded(DiscardReason.TOO_OLD, observedAt.toString());
        }

        Optional<StaticHeatSource> heatSource = staticHeatSources.near(latitude, longitude);
        if (heatSource.isPresent()) {
            return new Decision.Discarded(DiscardReason.STATIC_HEAT_SOURCE, heatSource.get().name());
        }

        Optional<Municipality> municipality = municipalities.locate(latitude, longitude);
        if (municipality.isEmpty()) {
            return new Decision.Discarded(DiscardReason.OUTSIDE_CANTABRIA, null);
        }

        return new Decision.Accepted(toEvent(hotspot, observedAt, municipality.get()));
    }

    private static IncidentEvent toEvent(
            FirmsHotspot hotspot, Instant observedAt, Municipality municipality) {

        return new IncidentEvent(
                IncidentEvent.CURRENT_SCHEMA_VERSION,
                hotspot.id(),
                INCIDENT_TYPE,
                SATELLITE_SEVERITY,
                municipality.zoneCode(),
                hotspot.latitude(),
                hotspot.longitude(),
                observedAt,
                IncidentSource.FIRMS,
                description(hotspot, municipality));
    }

    /**
     * E.g. "Foco detectado por satélite en Los Tojos: VIIRS (NOAA-21),
     * confianza nominal, potencia radiativa 1,5 MW."
     */
    static String description(FirmsHotspot hotspot, Municipality municipality) {
        String satellite = satelliteName(hotspot.satellite());
        String sensor = hotspot.instrument() == null
                ? satellite
                : hotspot.instrument() + " (" + satellite + ")";

        StringBuilder text = new StringBuilder()
                .append("Foco detectado por satélite en ").append(municipality.name())
                .append(": ").append(sensor)
                .append(", confianza ").append(confidenceName(hotspot.confidence()));

        if (hotspot.frp() != null) {
            text.append(String.format(SPANISH, ", potencia radiativa %.1f MW", hotspot.frp()));
        }
        return text.append('.').toString();
    }

    private static String satelliteName(String code) {
        if (code == null) {
            return "satélite desconocido";
        }
        return switch (code) {
            case "N" -> "Suomi NPP";
            case "N20" -> "NOAA-20";
            case "N21" -> "NOAA-21";
            default -> code;
        };
    }

    private static String confidenceName(String confidence) {
        return switch (normalized(confidence)) {
            case "l" -> "baja";
            case "n" -> "nominal";
            case "h" -> "alta";
            default -> confidence;
        };
    }

    private static void requireFields(FirmsHotspot hotspot) {
        if (hotspot == null || isBlank(hotspot.id())) {
            throw new InvalidHotspotException("El foco no tiene identificador");
        }
        if (hotspot.latitude() == null || hotspot.longitude() == null
                || isBlank(hotspot.acqDate()) || isBlank(hotspot.acqTime())
                || isBlank(hotspot.confidence())) {
            throw new InvalidHotspotException("Al foco " + hotspot.id()
                    + " le falta la posición, la fecha, la hora o la confianza");
        }
    }

    /** FIRMS gives the acquisition date and time in UTC. */
    private static Instant observedAt(FirmsHotspot hotspot) {
        try {
            return LocalDate.parse(hotspot.acqDate())
                    .atTime(LocalTime.parse(hotspot.acqTime(), ACQUISITION_TIME))
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeException exception) {
            throw new InvalidHotspotException("Fecha u hora de observación no válida en el foco "
                    + hotspot.id(), exception);
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
