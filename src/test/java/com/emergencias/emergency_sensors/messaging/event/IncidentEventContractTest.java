package com.emergencias.emergency_sensors.messaging.event;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins this service's copy of the {@code emergency-events} contract to the
 * one in the core service ({@code IncidentEvent} in
 * {@code emergency-management}, schema version 1). If the core changes its
 * contract, this test and {@link IncidentEvent} must change with it.
 */
class IncidentEventContractTest {

    /** Fields of the core's IncidentEvent, schema version 1. */
    private static final String[] CORE_FIELDS = {
            "schemaVersion", "id", "type", "severity", "zoneCode",
            "latitude", "longitude", "occurredAt", "source", "description"
    };

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void jsonHasExactlyTheFieldsOfTheCoreContract() {
        Map<String, Object> json = toJson(event("39086"));

        assertThat(json.keySet())
                .containsExactlyInAnyOrder(CORE_FIELDS);
    }

    @Test
    void valuesHaveTheFormatTheCoreReads() {
        Map<String, Object> json = toJson(event("39086"));

        assertThat(json.get("schemaVersion")).isEqualTo(1);
        assertThat(json.get("severity")).isEqualTo(0);
        assertThat(json.get("occurredAt")).isEqualTo("2026-11-22T03:02:00Z");
        assertThat(json.get("source")).isEqualTo("FIRMS");
    }

    @Test
    void missingZoneCodeIsSentAsNull() {
        Map<String, Object> json = toJson(event(null));

        assertThat(json).containsEntry("zoneCode", null);
    }

    @Test
    void schemaVersionAndSourcesMatchTheCore() {
        assertThat(IncidentEvent.CURRENT_SCHEMA_VERSION).isEqualTo(1);
        assertThat(Arrays.stream(IncidentSource.values()).map(Enum::name))
                .containsExactly("MANUAL", "FIRMS", "SIMULATOR");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toJson(IncidentEvent event) {
        return jsonMapper.readValue(jsonMapper.writeValueAsString(event), Map.class);
    }

    private static IncidentEvent event(String zoneCode) {
        return new IncidentEvent(
                IncidentEvent.CURRENT_SCHEMA_VERSION, "FIRMS-abc", "FOREST_FIRE", 0, zoneCode,
                43.10741, -4.19757, Instant.parse("2026-11-22T03:02:00Z"),
                IncidentSource.FIRMS, "Foco detectado por satélite");
    }
}
