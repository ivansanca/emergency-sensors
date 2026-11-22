package com.emergencias.emergency_sensors.detection;

/**
 * A hotspot message lacks a required field or has a date that cannot be read.
 * Retrying would fail again, so the error handler sends it straight to the
 * dead letter topic.
 */
public class InvalidHotspotException extends RuntimeException {

    public InvalidHotspotException(String message) {
        super(message);
    }

    public InvalidHotspotException(String message, Throwable cause) {
        super(message, cause);
    }
}
