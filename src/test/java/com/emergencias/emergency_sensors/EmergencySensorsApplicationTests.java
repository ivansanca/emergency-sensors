package com.emergencias.emergency_sensors;

import com.emergencias.emergency_sensors.integration.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the application context starts against a real Kafka. It needs
 * the broker because the service refuses to start when it cannot create its
 * topics ({@code spring.kafka.admin.fail-fast}).
 */
@IntegrationTest
class EmergencySensorsApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBean(EmergencySensorsApplication.class)).isNotNull();
    }
}
