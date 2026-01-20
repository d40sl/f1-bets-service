package com.f1bets.infrastructure.external.openf1;

import com.f1bets.infrastructure.external.openf1.dto.OpenF1Driver;
import com.f1bets.infrastructure.external.openf1.dto.OpenF1Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;

@Component
public class OpenF1Client {

    private static final Logger log = LoggerFactory.getLogger(OpenF1Client.class);
    private static final ParameterizedTypeReference<List<OpenF1Session>> SESSION_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<OpenF1Driver>> DRIVER_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String baseUrl;

    public OpenF1Client(
            RestClient.Builder restClientBuilder,
            @Value("${openf1.base-url}") String baseUrl,
            @Value("${openf1.timeout:5000}") int timeoutMs) {
        this.baseUrl = baseUrl;
        
        Duration timeout = Duration.ofMillis(timeoutMs);
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(timeout)
            .withReadTimeout(timeout);
        
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .requestFactory(ClientHttpRequestFactories.get(settings))
            .build();
        
        log.info("OpenF1Client initialized with {}ms timeout", timeoutMs);
    }

    public List<OpenF1Session> getSessions(String sessionType, Integer year, String countryCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/sessions");

        if (sessionType != null && !sessionType.isBlank()) {
            builder.queryParam("session_type", sessionType);
        }
        if (year != null) {
            builder.queryParam("year", year);
        }
        if (countryCode != null && !countryCode.isBlank()) {
            builder.queryParam("country_code", countryCode);
        }

        String uri = builder.build().toUriString();
        log.debug("Fetching sessions from OpenF1: {}", uri);

        List<OpenF1Session> sessions = restClient.get()
            .uri(uri)
            .retrieve()
            .body(SESSION_LIST_TYPE);

        return sessions != null ? sessions : List.of();
    }

    public List<OpenF1Driver> getDrivers(int sessionKey) {
        String uri = UriComponentsBuilder.fromPath("/drivers")
            .queryParam("session_key", sessionKey)
            .build()
            .toUriString();

        log.debug("Fetching drivers from OpenF1: {}", uri);

        List<OpenF1Driver> drivers = restClient.get()
            .uri(uri)
            .retrieve()
            .body(DRIVER_LIST_TYPE);

        return drivers != null ? drivers : List.of();
    }

    public List<OpenF1Session> getSessionByKey(int sessionKey) {
        String uri = UriComponentsBuilder.fromPath("/sessions")
            .queryParam("session_key", sessionKey)
            .build()
            .toUriString();

        log.debug("Fetching session by key from OpenF1: {}", uri);

        List<OpenF1Session> sessions = restClient.get()
            .uri(uri)
            .retrieve()
            .body(SESSION_LIST_TYPE);

        return sessions != null ? sessions : List.of();
    }
}
