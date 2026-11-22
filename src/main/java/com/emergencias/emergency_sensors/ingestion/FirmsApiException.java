package com.emergencias.emergency_sensors.ingestion;

/**
 * A FIRMS query failed or returned something that is not a hotspot CSV.
 * <p>
 * It never carries the original exception as its cause: the HTTP client puts
 * the request URL in its messages, and FIRMS puts the MAP_KEY in the URL.
 */
public class FirmsApiException extends RuntimeException {

    public FirmsApiException(String message) {
        super(message);
    }
}
