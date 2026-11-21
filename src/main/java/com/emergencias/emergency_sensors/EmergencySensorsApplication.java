package com.emergencias.emergency_sensors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the sensors service: adapts automatic fire sources and publishes
 * the detected fires to the core service through Kafka.
 */
@SpringBootApplication
public class EmergencySensorsApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmergencySensorsApplication.class, args);
	}

}
