package com.emergencias.emergency_sensors.detection;

import com.emergencias.emergency_sensors.messaging.event.IncidentEvent;

/**
 * Outcome of evaluating one hotspot: either an event for the core or the
 * reason it was dropped.
 */
public sealed interface Decision {

    record Accepted(IncidentEvent event) implements Decision {
    }

    /**
     * @param reason why it was dropped
     * @param detail extra information for the log, e.g. the name of the heat
     *               source; may be {@code null}
     */
    record Discarded(DiscardReason reason, String detail) implements Decision {
    }
}
