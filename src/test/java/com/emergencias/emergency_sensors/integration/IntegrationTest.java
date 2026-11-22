package com.emergencias.emergency_sensors.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that runs the whole application against a real Kafka started
 * in a throwaway container.
 * <p>
 * Every test class with this annotation shares the same Spring context, and
 * therefore the same container. The tests are skipped, not failed, when Docker
 * is not available; in GitHub Actions Docker is always available. Ingestion
 * is forced off and the key emptied instead of relying on the defaults, so no
 * test calls NASA whatever the properties file or the environment say.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sensors.ingestion.enabled=false", "sensors.firms.map-key="})
@Import(IntegrationTestContainers.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface IntegrationTest {
}
