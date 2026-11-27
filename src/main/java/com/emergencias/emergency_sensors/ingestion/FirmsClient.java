package com.emergencias.emergency_sensors.ingestion;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads the active fire CSV of one FIRMS source for the configured area,
 * for the last days or, when a date is configured, for the days from that
 * date on.
 * <p>
 * FIRMS answers errors with a 4xx status and a plain-text message, and a
 * correct answer is also {@code text/plain}, so the status is checked here
 * and the content is checked by {@link FirmsCsvParser}.
 * <p>
 * The MAP_KEY is part of the URL path, and the exceptions of Spring and of the
 * JDK client include the URL in their message. Every failure is therefore
 * replaced by a {@link FirmsApiException} that keeps only the exception type,
 * and the key is masked in every body before anything else reads it. Client
 * observations are off for the same reason: they record the full URL.
 * <p>
 * Limitation: the JVM option {@code -Djdk.internal.httpclient.debug=true} makes
 * the JDK client print the URL, key included, straight to stderr. It bypasses
 * the logging configuration, so it cannot be silenced from here: never turn it
 * on with a real key.
 */
public class FirmsClient {

    private static final int MAX_BODY_IN_MESSAGE = 200;

    private final RestClient restClient;
    private final FirmsProperties properties;

    public FirmsClient(RestClient.Builder builder, FirmsProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
        this.properties = properties;
    }

    public String fetchCsv(String dataset) {
        // The area goes into the template itself so that its commas are not
        // percent-encoded as they would be inside a URI variable.
        String path = "/api/area/csv/{mapKey}/{dataset}/"
                + properties.area() + "/{dayRange}";

        // With a date FIRMS returns that day and the next dayRange - 1 days;
        // without it, the last dayRange days.
        List<Object> variables = new ArrayList<>(
                List.of(properties.mapKey(), dataset, properties.dayRange()));
        if (properties.date() != null) {
            path += "/{date}";
            variables.add(properties.date());
        }

        try {
            return restClient.get()
                    .uri(path, variables.toArray())
                    .exchange((request, response) -> readBody(dataset, response));
        } catch (FirmsApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Not only RestClientException: for a host it cannot use, the JDK
            // throws IllegalArgumentException with the whole URL in the message.
            throw new FirmsApiException("No se pudo consultar FIRMS para "
                    + dataset + ": " + exception.getClass().getSimpleName());
        }
    }

    private String readBody(String dataset, ClientHttpResponse response)
            throws IOException {

        // Masked before it is shortened, or a key cut in half would get through.
        String body = mask(StreamUtils.copyToString(
                response.getBody(), StandardCharsets.UTF_8));

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new FirmsApiException("FIRMS respondió "
                    + response.getStatusCode().value() + " para " + dataset
                    + ": " + ExternalText.forMessage(body, MAX_BODY_IN_MESSAGE));
        }

        return body;
    }

    private String mask(String text) {
        return properties.hasMapKey()
                ? text.replace(properties.mapKey(), "***")
                : text;
    }
}
