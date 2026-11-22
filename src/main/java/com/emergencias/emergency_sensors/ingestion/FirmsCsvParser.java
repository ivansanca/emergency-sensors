package com.emergencias.emergency_sensors.ingestion;

import com.emergencias.emergency_sensors.messaging.event.FirmsHotspot;
import lombok.extern.slf4j.Slf4j;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a FIRMS area CSV into hotspots.
 * <p>
 * Columns are read by name, so the extra {@code type} column of the
 * standard-quality data, or any column FIRMS adds later, does not break it. A
 * response without the expected header is not a hotspot CSV (FIRMS sends some
 * errors as plain text with status 200) and is rejected as a whole; a header
 * with no rows simply means there were no fires. A single malformed row is
 * skipped and counted, but if no row is valid the query counts as failed:
 * otherwise a format change would look like a day without fires. An
 * unreadable optional value is left as {@code null}.
 * <p>
 * FIRMS writes the acquisition time without leading zeros ({@code 129} is
 * 01:29 UTC), so it is padded to four digits.
 */
@Slf4j
public class FirmsCsvParser {

    static final List<String> REQUIRED_COLUMNS = List.of(
            "latitude", "longitude", "acq_date", "acq_time",
            "satellite", "confidence", "daynight");

    private static final int MAX_TEXT_IN_MESSAGE = 100;
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    public List<FirmsHotspot> parse(String csv, String dataset, Instant fetchedAt) {
        List<String> lines = csv == null
                ? List.of()
                : withoutByteOrderMark(csv).lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty())
                        .toList();

        if (lines.isEmpty()) {
            throw new FirmsApiException(
                    "FIRMS devolvió una respuesta vacía para " + dataset);
        }

        Map<String, Integer> columns = columnIndexes(lines.getFirst());

        if (!columns.keySet().containsAll(REQUIRED_COLUMNS)) {
            throw new FirmsApiException("La respuesta de FIRMS para " + dataset
                    + " no es un CSV de focos: " + ExternalText.forMessage(lines.getFirst(), MAX_TEXT_IN_MESSAGE));
        }

        List<FirmsHotspot> hotspots = new ArrayList<>();
        int skipped = 0;

        for (String line : lines.subList(1, lines.size())) {
            List<String> values = List.of(line.split(",", -1));

            if (values.size() != columns.size()) {
                skipped++;
                continue;
            }

            try {
                hotspots.add(toHotspot(new Row(columns, values), dataset, fetchedAt));
            } catch (IllegalArgumentException | DateTimeException exception) {
                skipped++;
            }
        }

        if (skipped > 0) {
            if (hotspots.isEmpty()) {
                throw new FirmsApiException("Ninguna de las " + skipped
                        + " filas de FIRMS para " + dataset + " es válida");
            }
            log.warn("FIRMS {}: {} filas mal formadas descartadas", dataset, skipped);
        }

        return hotspots;
    }

    private static FirmsHotspot toHotspot(Row row, String dataset, Instant fetchedAt) {
        double latitude = Double.parseDouble(row.text("latitude"));
        double longitude = Double.parseDouble(row.text("longitude"));
        String acqDate = LocalDate.parse(row.text("acq_date")).toString();
        String acqTime = paddedTime(row.text("acq_time"));
        String satellite = row.text("satellite");
        String daynight = row.text("daynight");

        return new FirmsHotspot(
                DetectionIds.of(satellite, acqDate, daynight, latitude, longitude),
                dataset,
                latitude,
                longitude,
                row.decimal("bright_ti4"),
                row.decimal("scan"),
                row.decimal("track"),
                acqDate,
                acqTime,
                satellite,
                row.optionalText("instrument"),
                row.text("confidence"),
                row.optionalText("version"),
                row.decimal("bright_ti5"),
                row.decimal("frp"),
                daynight,
                row.integer("type"),
                fetchedAt);
    }

    static String paddedTime(String hhmm) {
        int value = Integer.parseInt(hhmm);
        LocalTime time = LocalTime.of(value / 100, value % 100);
        return String.format(Locale.ROOT, "%02d%02d", time.getHour(), time.getMinute());
    }

    private static Map<String, Integer> columnIndexes(String header) {
        String[] names = header.split(",", -1);
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            indexes.put(names[i].strip().toLowerCase(Locale.ROOT), i);
        }
        return indexes;
    }

    private static String withoutByteOrderMark(String text) {
        // strip() does not count it as white space.
        return text.startsWith(BYTE_ORDER_MARK)
                ? text.substring(BYTE_ORDER_MARK.length())
                : text;
    }

    private record Row(Map<String, Integer> columns, List<String> values) {

        String text(String column) {
            String value = optionalText(column);
            if (value == null) {
                throw new IllegalArgumentException("Falta el valor de " + column);
            }
            return value;
        }

        String optionalText(String column) {
            Integer index = columns.get(column);
            if (index == null) {
                return null;
            }
            String value = values.get(index).strip();
            return value.isEmpty() ? null : value;
        }

        // For optional columns only: a bad value must not lose the detection.
        Double decimal(String column) {
            String value = optionalText(column);
            if (value == null) {
                return null;
            }
            try {
                Double number = Double.valueOf(value);
                // NaN and Infinity would be written to the JSON as text.
                return Double.isFinite(number) ? number : null;
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        Integer integer(String column) {
            String value = optionalText(column);
            if (value == null) {
                return null;
            }
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}
