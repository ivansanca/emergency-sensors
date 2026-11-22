package com.emergencias.emergency_sensors.messaging.config;

import com.emergencias.emergency_sensors.detection.InvalidHotspotException;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventPublishingException;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventRejectedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.BackOffHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultBackOffHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Error handling of the fire detector. A failed hotspot is handled in one of
 * three ways:
 * <ul>
 *   <li>A message that cannot be read or lacks required fields, or an event
 *   that Kafka rejects for good (too large, for instance), goes straight to
 *   {@code sensor-satellite-hotspots-dlt}: retrying it would fail again.</li>
 *   <li>Any other failure to send the event of a hotspot that passed the
 *   filters (broker down, topic missing, not authorised...) is retried with an
 *   increasing delay and without limit. A possible fire must not be lost; the
 *   partition waits and carries on, in order, once the cause is fixed.</li>
 *   <li>Anything else, such as a bug, is retried a couple of times and then
 *   sent to the dead letter topic, so that it cannot block the partition
 *   forever.</li>
 * </ul>
 * Spring Kafka chooses how to retry a record on its first failure and only
 * chooses again when the type of the exception changes. That is why a
 * temporary failure and a rejected event are different exception classes.
 * <p>
 * Each failed attempt is logged as a warning with its cause, and each hotspot
 * sent to the dead letter topic as an error. The message itself is never
 * logged.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sensors.detector", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DetectorErrorHandlingConfig {

    static final long RETRY_INTERVAL_MS = 1000L;
    static final long MAX_RETRIES = 2L;
    static final long MAX_RETRY_INTERVAL_MS = 30_000L;
    static final double RETRY_MULTIPLIER = 2.0;

    private static final int MAX_CAUSE_MESSAGE = 150;

    @Bean
    DefaultErrorHandler detectorErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        return errorHandler(
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        DetectorErrorHandlingConfig::deadLetterDestination),
                new DefaultBackOffHandler());
    }

    static DefaultErrorHandler errorHandler(
            ConsumerRecordRecoverer recoverer, BackOffHandler backOffHandler) {

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES),
                backOffHandler);

        errorHandler.setBackOffFunction((rejected, exception) -> backOffFor(exception));
        errorHandler.addNotRetryableExceptions(
                InvalidHotspotException.class,
                IncidentEventRejectedException.class,
                JacksonException.class);
        errorHandler.setRetryListeners(new LoggingRetryListener());

        return errorHandler;
    }

    /**
     * Back-off without limit for a failure to send the event, or {@code null}
     * to use the default one.
     */
    static BackOff backOffFor(Exception exception) {
        if (!causedBy(exception, IncidentEventPublishingException.class)) {
            return null;
        }

        ExponentialBackOff backOff = new ExponentialBackOff(RETRY_INTERVAL_MS, RETRY_MULTIPLIER);
        backOff.setMaxInterval(MAX_RETRY_INTERVAL_MS);
        return backOff;
    }

    /**
     * Partition -1 lets Kafka choose the partition, so the dead letter topic
     * does not need as many partitions as the raw topic.
     */
    static TopicPartition deadLetterDestination(
            ConsumerRecord<?, ?> rejected, Exception exception) {

        return new TopicPartition(KafkaTopics.SATELLITE_HOTSPOTS_DLT, -1);
    }

    /**
     * Types of the cause chain, with the message of the Kafka errors and of
     * this service's own ones, which never quote the hotspot. Other messages
     * are left out because they may (Jackson's do, for instance).
     */
    static String describe(Throwable failure) {
        List<String> parts = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof ListenerExecutionFailedException) {
                continue;
            }
            String type = cause.getClass().getName();
            String part = cause.getClass().getSimpleName();
            if (cause.getMessage() != null
                    && (type.startsWith("org.apache.kafka.") || type.startsWith("com.emergencias."))) {
                String message = cause.getMessage().replaceAll("\\s+", " ");
                part += ": " + message.substring(0, Math.min(message.length(), MAX_CAUSE_MESSAGE));
            }
            parts.add(part);
        }
        return String.join(" <- ", parts);
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    /** Logs every failed attempt and every hotspot sent to the dead letter topic. */
    static class LoggingRetryListener implements RetryListener {

        @Override
        public void failedDelivery(ConsumerRecord<?, ?> rejected, Exception exception, int deliveryAttempt) {
            log.warn("Foco {}: falló el intento {} ({})",
                    rejected.key(), deliveryAttempt, describe(exception));
        }

        @Override
        public void recovered(ConsumerRecord<?, ?> rejected, Exception exception) {
            log.error("Foco {} enviado a {} ({})",
                    rejected.key(), KafkaTopics.SATELLITE_HOTSPOTS_DLT, describe(exception));
        }

        @Override
        public void recoveryFailed(ConsumerRecord<?, ?> rejected, Exception original, Exception failure) {
            log.error("Foco {}: no se pudo enviar a {} ({}); se volverá a intentar",
                    rejected.key(), KafkaTopics.SATELLITE_HOTSPOTS_DLT, describe(failure));
        }
    }
}
