package com.emergencias.emergency_sensors.messaging.event;

/**
 * Origin of an incident event. Same values as the enum of the core service,
 * which rejects any other.
 */
public enum IncidentSource {
    MANUAL,
    FIRMS,
    SIMULATOR
}
