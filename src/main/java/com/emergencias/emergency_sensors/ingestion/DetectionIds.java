package com.emergencias.emergency_sensors.ingestion;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds the id that identifies the same FIRMS detection across queries.
 * <p>
 * The id is {@code FIRMS-} plus the SHA-256 of satellite, date, day/night flag
 * and coordinates. The acquisition time is left out on purpose: the same
 * detection arrives in the near-real-time and in the standard-quality data
 * with the time shifted by one or two minutes, and including it would turn one
 * detection into two.
 * <p>
 * Coordinates are rounded to five decimals with {@link BigDecimal}, which does
 * not depend on the default locale ({@code String.format} would write a
 * decimal comma on a Spanish system and change every id).
 * <p>
 * Changing this formula changes the id of every detection already published,
 * so a test pins a known value.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DetectionIds {

    static final String PREFIX = "FIRMS-";
    private static final int COORDINATE_DECIMALS = 5;

    public static String of(
            String satellite,
            String acqDate,
            String daynight,
            double latitude,
            double longitude) {

        String key = String.join("|",
                satellite,
                acqDate,
                daynight,
                coordinate(latitude),
                coordinate(longitude));

        return PREFIX + HexFormat.of().formatHex(sha256(key));
    }

    static String coordinate(double value) {
        return BigDecimal.valueOf(value)
                .setScale(COORDINATE_DECIMALS, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }
}
