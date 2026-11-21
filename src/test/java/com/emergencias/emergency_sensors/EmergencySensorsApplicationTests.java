package com.emergencias.emergency_sensors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the application context starts.
 */
@SpringBootTest
class EmergencySensorsApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBean(EmergencySensorsApplication.class)).isNotNull();
    }
}
