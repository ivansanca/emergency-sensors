package com.emergencias.emergency_sensors.ingestion;

import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses the real FIRMS answers for Cantabria on 21-22 March 2026 as fixtures.
 */
class FirmsCsvParserTest {

    static final String NRT_HEADER = "latitude,longitude,bright_ti4,scan,track,acq_date,"
            + "acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight";

    private static final String NRT_ROW = "43.10741,-4.19757,316.59,0.39,0.36,2026-03-21,"
            + "219,N21,VIIRS,n,2.0NRT,277.4,1.54,N";
    private static final Instant FETCHED_AT = Instant.parse("2026-03-22T18:00:00Z");

    private final FirmsCsvParser parser = new FirmsCsvParser();

    @Test
    void parsesNearRealTimeSample() throws IOException {
        List<FirmsHotspot> hotspots = parser.parse(
                fixture("2026-03-21_noaa21_nrt.csv"), "VIIRS_NOAA21_NRT", FETCHED_AT);

        assertThat(hotspots).hasSize(55);

        FirmsHotspot first = hotspots.getFirst();
        assertThat(first.id()).isEqualTo(
                DetectionIds.of("N21", "2026-03-21", "N", 43.10741, -4.19757));
        assertThat(first.dataset()).isEqualTo("VIIRS_NOAA21_NRT");
        assertThat(first.latitude()).isEqualTo(43.10741);
        assertThat(first.longitude()).isEqualTo(-4.19757);
        assertThat(first.brightTi4()).isEqualTo(316.59);
        assertThat(first.acqDate()).isEqualTo("2026-03-21");
        assertThat(first.acqTime()).isEqualTo("0219");
        assertThat(first.satellite()).isEqualTo("N21");
        assertThat(first.instrument()).isEqualTo("VIIRS");
        assertThat(first.confidence()).isEqualTo("n");
        assertThat(first.version()).isEqualTo("2.0NRT");
        assertThat(first.frp()).isEqualTo(1.54);
        assertThat(first.daynight()).isEqualTo("N");
        assertThat(first.type()).isNull();
        assertThat(first.fetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void parsesStandardQualitySampleWithTypeColumn() throws IOException {
        List<FirmsHotspot> noaa20 = parser.parse(
                fixture("2026-03-21_noaa20_sp.csv"), "VIIRS_NOAA20_SP", FETCHED_AT);
        List<FirmsHotspot> snpp = parser.parse(
                fixture("2026-03-21_snpp_sp.csv"), "VIIRS_SNPP_SP", FETCHED_AT);

        assertThat(noaa20).hasSize(66);
        assertThat(snpp).hasSize(53);

        FirmsHotspot first = noaa20.getFirst();
        assertThat(first.acqTime()).isEqualTo("0129");
        assertThat(first.satellite()).isEqualTo("N20");
        assertThat(first.type()).isZero();
    }

    @Test
    void headerWithoutRowsMeansNoFires() {
        assertThat(parser.parse(NRT_HEADER + "\n", "VIIRS_NOAA20_NRT", FETCHED_AT))
                .isEmpty();
    }

    @Test
    void rejectsPlainTextErrors() {
        assertThatThrownBy(() -> parser.parse(
                "Invalid day range. Expects [1..5].", "VIIRS_NOAA20_NRT", FETCHED_AT))
                .isInstanceOf(FirmsApiException.class)
                .hasMessageContaining("no es un CSV de focos")
                .hasMessageContaining("Invalid day range");
    }

    @Test
    void rejectsEmptyResponses() {
        assertThatThrownBy(() -> parser.parse("  \n", "VIIRS_NOAA20_NRT", FETCHED_AT))
                .isInstanceOf(FirmsApiException.class)
                .hasMessageContaining("vacía");

        assertThatThrownBy(() -> parser.parse(null, "VIIRS_NOAA20_NRT", FETCHED_AT))
                .isInstanceOf(FirmsApiException.class);
    }

    @Test
    void skipsMalformedRowsAndKeepsTheRest() {
        String csv = NRT_HEADER + "\n"
                + "43.10741,-4.19757,316.59,0.39,0.36,2026-03-21,219,N21,VIIRS,n,2.0NRT,277.4,1.54,N\n"
                + "43.1,-4.1,300.0,0.4,0.4,2026-03-21\n"
                + "abc,-4.1,300.0,0.4,0.4,2026-03-21,219,N21,VIIRS,n,2.0NRT,277.4,1.54,N\n"
                + "43.1,-4.1,300.0,0.4,0.4,2026-13-40,219,N21,VIIRS,n,2.0NRT,277.4,1.54,N\n"
                + "43.1,-4.1,300.0,0.4,0.4,2026-03-21,2575,N21,VIIRS,n,2.0NRT,277.4,1.54,N\n"
                + ",-4.1,300.0,0.4,0.4,2026-03-21,219,N21,VIIRS,n,2.0NRT,277.4,1.54,N\n";

        assertThat(parser.parse(csv, "VIIRS_NOAA21_NRT", FETCHED_AT))
                .singleElement()
                .extracting(FirmsHotspot::latitude)
                .isEqualTo(43.10741);
    }

    @Test
    void emptyOptionalColumnsStayNull() {
        String csv = NRT_HEADER + "\n"
                + "43.10741,-4.19757,,,,2026-03-21,219,N21,,n,,,,N\n";

        FirmsHotspot hotspot = parser.parse(csv, "VIIRS_NOAA21_NRT", FETCHED_AT).getFirst();

        assertThat(hotspot.brightTi4()).isNull();
        assertThat(hotspot.frp()).isNull();
        assertThat(hotspot.instrument()).isNull();
        assertThat(hotspot.version()).isNull();
    }

    @Test
    void acceptsWindowsLineEndingsAndTrailingBlankLines() {
        String csv = NRT_HEADER + "\r\n" + NRT_ROW + "\r\n\r\n\r\n";

        assertThat(parser.parse(csv, "VIIRS_NOAA21_NRT", FETCHED_AT))
                .singleElement()
                .extracting(FirmsHotspot::daynight)
                .isEqualTo("N");
    }

    @Test
    void ignoresAByteOrderMark() {
        String csv = "\uFEFF" + NRT_HEADER + "\n" + NRT_ROW + "\n";

        assertThat(parser.parse(csv, "VIIRS_NOAA21_NRT", FETCHED_AT))
                .singleElement()
                .extracting(FirmsHotspot::latitude)
                .isEqualTo(43.10741);
    }

    @Test
    void readsColumnsByNameWhateverTheirOrder() {
        String csv = "daynight,frp,satellite,confidence,acq_time,acq_date,longitude,latitude\n"
                + "N,1.54,N21,n,219,2026-03-21,-4.19757,43.10741\n";

        FirmsHotspot hotspot = parser.parse(csv, "VIIRS_NOAA21_NRT", FETCHED_AT).getFirst();

        assertThat(hotspot.id()).isEqualTo(
                DetectionIds.of("N21", "2026-03-21", "N", 43.10741, -4.19757));
        assertThat(hotspot.frp()).isEqualTo(1.54);
    }

    @Test
    void padsAcquisitionTimeToFourDigits() {
        assertThat(FirmsCsvParser.paddedTime("129")).isEqualTo("0129");
        assertThat(FirmsCsvParser.paddedTime("5")).isEqualTo("0005");
        assertThat(FirmsCsvParser.paddedTime("1415")).isEqualTo("1415");
    }

    private String fixture(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/firms/" + name)) {
            assertThat(input).as("fixture %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
