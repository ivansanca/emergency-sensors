package com.emergencias.emergency_sensors.ingestion;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * Prepares text received from outside to be quoted in a log or error message:
 * one line, so that it cannot forge log entries, and of limited length.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExternalText {

    private static final Pattern LINE_BREAKS_AND_CONTROLS =
            Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}]");

    static String forMessage(String text, int maxLength) {
        String line = LINE_BREAKS_AND_CONTROLS.matcher(text).replaceAll(" ").strip();
        return line.length() <= maxLength
                ? line
                : line.substring(0, maxLength) + "…";
    }
}
