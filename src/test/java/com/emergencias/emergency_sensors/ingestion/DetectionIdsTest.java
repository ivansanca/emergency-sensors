package com.emergencias.emergency_sensors.ingestion;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the id formula and checks what makes two detections the same one.
 */
class DetectionIdsTest {

    @Test
    void knownDetectionKeepsItsId() {
        // Pinned on purpose: changing the formula would duplicate every
        // detection already published.
        assertThat(DetectionIds.of("N21", "2026-03-21", "N", 43.10741, -4.19757))
                .isEqualTo("FIRMS-9f28434cdddcec86d470145e5beb898a66f1a20a17b96d83a51c03a2172fc877");
    }

    @Test
    void coordinatesAreRoundedToFiveDecimals() {
        assertThat(DetectionIds.of("N20", "2026-03-21", "D", 43.144631, -3.855014))
                .isEqualTo(DetectionIds.of("N20", "2026-03-21", "D", 43.14463, -3.85501));

        assertThat(DetectionIds.coordinate(43.1446)).isEqualTo("43.14460");
        assertThat(DetectionIds.coordinate(-4.0)).isEqualTo("-4.00000");
    }

    @Test
    void differentDetectionsGetDifferentIds() {
        String id = DetectionIds.of("N21", "2026-03-21", "N", 43.10741, -4.19757);

        assertThat(DetectionIds.of("N20", "2026-03-21", "N", 43.10741, -4.19757)).isNotEqualTo(id);
        assertThat(DetectionIds.of("N21", "2026-03-22", "N", 43.10741, -4.19757)).isNotEqualTo(id);
        assertThat(DetectionIds.of("N21", "2026-03-21", "D", 43.10741, -4.19757)).isNotEqualTo(id);
        assertThat(DetectionIds.of("N21", "2026-03-21", "N", 43.10742, -4.19757)).isNotEqualTo(id);
    }

    @Test
    void doesNotDependOnTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("es", "ES"));

            assertThat(DetectionIds.of("N21", "2026-03-21", "N", 43.10741, -4.19757))
                    .isEqualTo("FIRMS-9f28434cdddcec86d470145e5beb898a66f1a20a17b96d83a51c03a2172fc877");
            assertThat(DetectionIds.coordinate(43.1)).isEqualTo("43.10000");
        } finally {
            Locale.setDefault(original);
        }
    }
}
