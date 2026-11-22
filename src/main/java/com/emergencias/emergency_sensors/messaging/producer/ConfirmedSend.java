package com.emergencias.emergency_sensors.messaging.producer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Sends a message and waits until Kafka confirms it. The publishers of this
 * service only differ in the topic and in the exception they report.
 * <p>
 * The wait is longer than {@code delivery.timeout.ms}, so the producer stops
 * retrying before this class gives up. A request already on the wire may still
 * be written by the broker, so a message reported as failed can arrive twice;
 * consumers tolerate it because the key is the detection id. {@code send()}
 * itself may block up to {@code max.block.ms} before the wait starts.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConfirmedSend {

    static final long TIMEOUT_SECONDS = 10;

    static void sendAndWait(
            KafkaTemplate<String, String> kafkaTemplate,
            String topic,
            String key,
            String message,
            Function<Throwable, ? extends RuntimeException> failure) {

        try {
            kafkaTemplate.send(topic, key, message)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure.apply(exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            // RuntimeException: send() throws instead of failing the future
            // when the broker is down or the thread is interrupted.
            throw failure.apply(exception);
        }
    }
}
