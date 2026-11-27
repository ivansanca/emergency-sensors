package com.emergencias.emergency_sensors.ingestion;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Talks to a WireMock server that plays the FIRMS API, so the tests do not
 * depend on NASA or on a real key.
 */
class FirmsClientTest {

    private static final String MAP_KEY = "0123456789abcdef0123456789abcdef";
    private static final String DATASET = "VIIRS_NOAA20_NRT";
    private static final String AREA = "-4.95,42.65,-3.05,43.60";
    private static final String PATH =
            "/api/area/csv/" + MAP_KEY + "/" + DATASET + "/" + AREA + "/2";

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void returnsTheCsvOfTheRequestedSourceAndArea() {
        String csv = FirmsCsvParserTest.NRT_HEADER + "\n";
        server.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(csv)));

        assertThat(client(Duration.ofSeconds(5)).fetchCsv(DATASET)).isEqualTo(csv);
        server.verify(getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void withADateTheQueryCoversTheDaysFromThatDate() {
        String csv = FirmsCsvParserTest.NRT_HEADER + "\n";
        server.stubFor(get(urlEqualTo(PATH + "/2026-03-21"))
                .willReturn(aResponse().withStatus(200).withBody(csv)));

        FirmsClient client = client(
                server.baseUrl(), Duration.ofSeconds(5), LocalDate.of(2026, 3, 21));

        assertThat(client.fetchCsv(DATASET)).isEqualTo(csv);
        server.verify(getRequestedFor(urlEqualTo(PATH + "/2026-03-21")));
    }

    @Test
    void errorStatusBecomesFirmsApiException() {
        server.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("Invalid day range. Expects [1..5].")));

        FirmsApiException exception = fetchExpectingFailure(Duration.ofSeconds(5));

        assertThat(exception.getMessage())
                .contains("400")
                .contains(DATASET)
                .contains("Invalid day range");
    }

    @Test
    void keyEchoedInTheErrorBodyIsMasked() {
        server.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("Invalid MAP_KEY: " + MAP_KEY)));

        FirmsApiException exception = fetchExpectingFailure(Duration.ofSeconds(5));

        assertThat(exception.getMessage()).doesNotContain(MAP_KEY).contains("***");
    }

    @Test
    void timeoutDoesNotLeakTheKey() {
        server.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        FirmsApiException exception = fetchExpectingFailure(Duration.ofMillis(200));

        assertThat(exception.getMessage()).contains(DATASET).doesNotContain(MAP_KEY);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void unreachableServerDoesNotLeakTheKey() {
        FirmsClient client = client(Duration.ofSeconds(5));
        server.stop();

        FirmsApiException exception = catchThrowableOfType(
                FirmsApiException.class, () -> client.fetchCsv(DATASET));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).doesNotContain(MAP_KEY);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void keyEchoedWithStatus200DoesNotReachTheParserMessage() {
        server.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Invalid MAP_KEY: " + MAP_KEY)));
        String body = client(Duration.ofSeconds(5)).fetchCsv(DATASET);
        FirmsCsvParser parser = new FirmsCsvParser();

        // The parser quotes the first line, and the poller logs that message.
        FirmsApiException exception = catchThrowableOfType(
                FirmsApiException.class, () -> parser.parse(body, DATASET, Instant.EPOCH));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("***").doesNotContain(MAP_KEY);
    }

    @Test
    void unusableHostDoesNotLeakTheKey() {
        // The JDK rejects the underscore before any DNS lookup, with an
        // IllegalArgumentException that quotes the whole URL.
        FirmsClient client = client("http://firms_proxy:8080", Duration.ofSeconds(5));

        FirmsApiException exception = catchThrowableOfType(
                FirmsApiException.class, () -> client.fetchCsv(DATASET));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(DATASET).doesNotContain(MAP_KEY);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void keyAcrossTheTruncationLimitIsMaskedWhole() {
        // The message keeps 200 characters of the body: the key straddles that limit.
        server.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("x".repeat(180) + MAP_KEY)));

        FirmsApiException exception = fetchExpectingFailure(Duration.ofSeconds(5));

        assertThat(exception.getMessage())
                .contains("***")
                .doesNotContain(MAP_KEY.substring(0, 8));
    }

    private FirmsApiException fetchExpectingFailure(Duration readTimeout) {
        FirmsClient client = client(readTimeout);

        FirmsApiException exception = catchThrowableOfType(
                FirmsApiException.class, () -> client.fetchCsv(DATASET));

        assertThat(exception).isNotNull();
        return exception;
    }

    private FirmsClient client(Duration readTimeout) {
        return client(server.baseUrl(), readTimeout);
    }

    private FirmsClient client(String baseUrl, Duration readTimeout) {
        return client(baseUrl, readTimeout, null);
    }

    private FirmsClient client(String baseUrl, Duration readTimeout, LocalDate date) {
        FirmsProperties properties = new FirmsProperties(
                MAP_KEY,
                baseUrl,
                List.of(DATASET),
                AREA,
                2,
                date,
                Duration.ofSeconds(2),
                readTimeout,
                Duration.ofHours(48));

        return new FirmsClient(RestClient.builder(), properties);
    }
}
