package com.emergencias.emergency_sensors.messaging.config;

import com.emergencias.emergency_sensors.detection.InvalidHotspotException;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventPublishingException;
import com.emergencias.emergency_sensors.messaging.producer.IncidentEventRejectedException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.BackOffHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Checks which failures the detector retries without limit and which go to
 * the dead letter topic, with the real error handler of Spring Kafka. The
 * back-off delays are skipped, not slept.
 */
class DetectorErrorHandlingConfigTest {

    private static final BackOffHandler NO_WAIT = new BackOffHandler() {
        @Override
        public void onNextBackOff(MessageListenerContainer container, Exception exception, long nextBackOff) {
            // The tests only count attempts.
        }
    };

    private final ConsumerRecordRecoverer recoverer = mock(ConsumerRecordRecoverer.class);
    private final DefaultErrorHandler errorHandler =
            DetectorErrorHandlingConfig.errorHandler(recoverer, NO_WAIT);

    private final ConsumerRecord<String, String> hotspot =
            new ConsumerRecord<>(KafkaTopics.SATELLITE_HOTSPOTS, 0, 7L, "FIRMS-abc", "{}");

    @Test
    void brokerDownIsRetriedWithoutLimit() {
        assertThat(retriesBeforeGivingUp(temporary(new org.apache.kafka.common.errors.TimeoutException(
                "Topic emergency-events not present in metadata")), 200)).isFalse();
        verify(recoverer, never()).accept(any(), any());
    }

    @Test
    void writeNotAuthorisedIsRetriedWithoutLimit() {
        assertThat(retriesBeforeGivingUp(temporary(new TopicAuthorizationException("emergency-events")), 200))
                .isFalse();
        verify(recoverer, never()).accept(any(), any());
    }

    @Test
    void eventRejectedForGoodGoesToTheDeadLetterTopicAtOnce() {
        assertThat(handle(rejected())).isTrue();
        verify(recoverer).accept(any(), any());
    }

    @Test
    void temporaryFailureAfterARejectionIsStillRetried() {
        // A1: the first attempt was rejected and sent to the dead letter topic;
        // a later hotspot that only finds the topic missing must not follow it.
        handle(rejected());
        ConsumerRecord<String, String> next =
                new ConsumerRecord<>(KafkaTopics.SATELLITE_HOTSPOTS, 0, 8L, "FIRMS-def", "{}");

        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(errorHandler.handleOne(temporary(new TimeoutException()), next, consumer(), container()))
                    .isFalse();
        }
    }

    @Test
    void rejectionAfterTemporaryFailuresStopsRetrying() {
        // B1: retrying without limit must end when the cause becomes the event itself.
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(handle(temporary(new TimeoutException()))).isFalse();
        }

        assertThat(handle(rejected())).isTrue();
        verify(recoverer).accept(any(), any());
    }

    @Test
    void temporaryFailureAfterABugIsRetriedWithoutLimit() {
        assertThat(handle(new ListenerExecutionFailedException("bug", new IllegalStateException()))).isFalse();

        assertThat(retriesBeforeGivingUp(temporary(new TimeoutException()), 100)).isFalse();
    }

    @Test
    void bugIsRetriedTwiceAndThenSentToTheDeadLetterTopic() {
        Exception bug = new ListenerExecutionFailedException("bug", new IllegalStateException());

        assertThat(handle(bug)).isFalse();
        assertThat(handle(bug)).isFalse();
        assertThat(handle(bug)).isTrue();
    }

    @Test
    void invalidHotspotGoesToTheDeadLetterTopicAtOnce() {
        assertThat(handle(new ListenerExecutionFailedException("invalid",
                new InvalidHotspotException("Al foco le falta la posición")))).isTrue();
    }

    @Test
    void sendFailureBacksOffWithIncreasingDelayAndWithoutLimit() {
        BackOff backOff = DetectorErrorHandlingConfig.backOffFor(temporary(new TimeoutException()));

        assertThat(backOff).isInstanceOf(ExponentialBackOff.class);
        BackOffExecution execution = backOff.start();
        assertThat(execution.nextBackOff()).isEqualTo(DetectorErrorHandlingConfig.RETRY_INTERVAL_MS);
        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(execution.nextBackOff())
                    .isNotEqualTo(BackOffExecution.STOP)
                    .isLessThanOrEqualTo(DetectorErrorHandlingConfig.MAX_RETRY_INTERVAL_MS);
        }
    }

    @Test
    void descriptionKeepsKafkaMessagesButNotOtherText() {
        Exception failure = new ListenerExecutionFailedException("Listener failed",
                new IncidentEventPublishingException("FIRMS-abc", new ExecutionException(
                        new KafkaException("Send failed", new TopicAuthorizationException("Not authorized")))));

        assertThat(DetectorErrorHandlingConfig.describe(failure)).isEqualTo(
                "IncidentEventPublishingException: Kafka no confirmó el evento del foco FIRMS-abc"
                        + " <- ExecutionException <- KafkaException"
                        + " <- TopicAuthorizationException: Not authorized");
    }

    @Test
    void rejectedHotspotsGoToTheDeadLetterTopicInAnyPartition() {
        ConsumerRecord<String, String> rejected =
                new ConsumerRecord<>(KafkaTopics.SATELLITE_HOTSPOTS, 3, 42L, "FIRMS-abc", "{}");

        assertThat(DetectorErrorHandlingConfig.deadLetterDestination(rejected, new IllegalStateException()))
                .isEqualTo(new TopicPartition(KafkaTopics.SATELLITE_HOTSPOTS_DLT, -1));
    }

    /** Whether the error handler gives up within the given number of attempts. */
    private boolean retriesBeforeGivingUp(Exception failure, int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (handle(failure)) {
                return true;
            }
        }
        return false;
    }

    private boolean handle(Exception failure) {
        return errorHandler.handleOne(failure, hotspot, consumer(), container());
    }

    private static Exception temporary(Exception cause) {
        return new ListenerExecutionFailedException("Listener failed",
                new IncidentEventPublishingException("FIRMS-abc", new ExecutionException(cause)));
    }

    private static Exception rejected() {
        return new ListenerExecutionFailedException("Listener failed",
                new IncidentEventRejectedException("FIRMS-abc", new ExecutionException(
                        new RecordTooLargeException("too large"))));
    }

    @SuppressWarnings("unchecked")
    private static Consumer<String, String> consumer() {
        return mock(Consumer.class);
    }

    private static MessageListenerContainer container() {
        return mock(MessageListenerContainer.class);
    }
}
