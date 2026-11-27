package com.emergencias.emergency_sensors.ingestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Settings of the NASA FIRMS ingestion ({@code sensors.firms.*}).
 * <p>
 * The map key comes from the {@code FIRMS_MAP_KEY} environment variable and
 * may be empty: without it the poller logs a warning and does nothing, so the
 * rest of the service still starts. {@link #toString()} hides the key because
 * a record would otherwise print it.
 * <p>
 * The base URL and the area are checked at startup: both end up in the request
 * URI, the area as part of its template.
 * <p>
 * {@code date} is optional. Without it each query covers the last
 * {@code dayRange} days, which is what live ingestion needs. With it the
 * queries cover {@code dayRange} days starting on that date, so that a past
 * day can be replayed through the whole pipeline. A date that cannot be read
 * as {@code yyyy-MM-dd} stops the startup.
 *
 * @param mapKey        FIRMS MAP_KEY; it travels in the URL path
 * @param baseUrl       base URL of the FIRMS API
 * @param datasets      FIRMS sources to query, e.g. {@code VIIRS_NOAA20_NRT}
 * @param area          west,south,east,north bounding box
 * @param dayRange      days per query; 1 is only the current UTC day
 * @param date          first day of the queries, or {@code null} for the
 *                      last {@code dayRange} days
 * @param connectTimeout connection timeout of each request
 * @param readTimeout   time allowed to receive the response
 * @param sentCacheTtl  how long a published detection id is remembered
 */
@Validated
@ConfigurationProperties(prefix = "sensors.firms")
public record FirmsProperties(
        String mapKey,
        @NotBlank
        @Pattern(regexp = BASE_URL_FORMAT,
                message = "debe ser una URL http(s) con un host válido (sin guiones bajos)")
        String baseUrl,
        @NotEmpty List<@NotBlank String> datasets,
        @NotBlank
        @Pattern(regexp = AREA_FORMAT,
                message = "debe tener cuatro números separados por comas: oeste,sur,este,norte")
        String area,
        @Min(1) @Max(5) int dayRange,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration sentCacheTtl) {

    private static final String BASE_URL_FORMAT =
            "https?://[A-Za-z0-9.-]+(:\\d{1,5})?(/[A-Za-z0-9./-]*)?";
    private static final String AREA_FORMAT =
            "-?\\d{1,3}(\\.\\d+)?(,-?\\d{1,3}(\\.\\d+)?){3}";

    public boolean hasMapKey() {
        return StringUtils.hasText(mapKey);
    }

    @Override
    public String toString() {
        return "FirmsProperties[mapKey=" + (hasMapKey() ? "***" : "")
                + ", baseUrl=" + baseUrl
                + ", datasets=" + datasets
                + ", area=" + area
                + ", dayRange=" + dayRange
                + ", date=" + date
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", sentCacheTtl=" + sentCacheTtl + "]";
    }
}
