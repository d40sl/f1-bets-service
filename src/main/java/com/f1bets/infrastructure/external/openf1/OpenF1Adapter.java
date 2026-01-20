package com.f1bets.infrastructure.external.openf1;

import com.f1bets.application.dto.EventWithDrivers;
import com.f1bets.application.dto.EventWithDrivers.DriverInfo;
import com.f1bets.application.dto.SessionQuery;
import com.f1bets.application.port.F1DataProvider;
import com.f1bets.application.service.OddsCalculator;
import com.f1bets.domain.exception.ExternalServiceUnavailableException;
import com.f1bets.infrastructure.external.openf1.dto.OpenF1Driver;
import com.f1bets.infrastructure.external.openf1.dto.OpenF1Session;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Component
public class OpenF1Adapter implements F1DataProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenF1Adapter.class);

    private final OpenF1Client client;
    private final Cache<SessionQuery, List<EventWithDrivers>> sessionCache;
    private final Cache<Integer, Optional<EventWithDrivers>> sessionKeyCache;
    private final OddsCalculator oddsCalculator;
    private final int cacheTtlSeconds;
    private final int maxSessions;
    private final int driverDelayMs;

    public OpenF1Adapter(OpenF1Client client,
                         Cache<SessionQuery, List<EventWithDrivers>> sessionCache,
                         Cache<Integer, Optional<EventWithDrivers>> sessionKeyCache,
                         OddsCalculator oddsCalculator,
                         @Value("${openf1.cache-ttl:180}") int cacheTtlSeconds,
                         @Value("${openf1.max-sessions:6}") int maxSessions,
                         @Value("${openf1.driver-delay-ms:500}") int driverDelayMs) {
        this.client = client;
        this.sessionCache = sessionCache;
        this.sessionKeyCache = sessionKeyCache;
        this.oddsCalculator = oddsCalculator;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.maxSessions = maxSessions;
        this.driverDelayMs = driverDelayMs;
    }

    @PostConstruct
    public void logConfiguration() {
        log.info("OpenF1Adapter initialized - cacheTTL: {}s, maxSessions: {}, driverDelayMs: {}ms, " +
                 "resilience: CircuitBreaker[openf1] + Retry[openf1] + Bulkhead[openf1]",
                 cacheTtlSeconds, maxSessions, driverDelayMs);
    }

    @Override
    @CircuitBreaker(name = "openf1", fallbackMethod = "getSessionsFallback")
    @Bulkhead(name = "openf1")
    @Retry(name = "openf1")
    public List<EventWithDrivers> getSessions(SessionQuery query) {
        return getSessions(query, false);
    }

    @Override
    @CircuitBreaker(name = "openf1", fallbackMethod = "getSessionsFallbackWithSkipCache")
    @Bulkhead(name = "openf1")
    @Retry(name = "openf1")
    public List<EventWithDrivers> getSessions(SessionQuery query, boolean skipCache) {
        if (!skipCache) {
            List<EventWithDrivers> cached = sessionCache.getIfPresent(query);
            if (cached != null) {
                log.debug("Returning cached sessions for query: {}", query);
                return cached;
            }
        } else {
            log.debug("Skipping cache for query: {}", query);
        }

        List<OpenF1Session> sessions = client.getSessions(
            query.sessionType(),
            query.year(),
            query.countryCode()
        );

        // Limit sessions to avoid excessive API calls to OpenF1 (rate limit: 3 req/sec)
        // Each session requires a driver fetch, so limit to 6 most recent sessions
        int effectiveMaxSessions = maxSessions;
        if (effectiveMaxSessions > 0 && sessions.size() > effectiveMaxSessions) {
            log.info("Limiting sessions from {} to {} to respect OpenF1 rate limits",
                     sessions.size(), effectiveMaxSessions);
            sessions = sessions.subList(sessions.size() - effectiveMaxSessions, sessions.size());
        }

        List<EventWithDrivers> events = fetchDriversSequentially(sessions);

        if (!skipCache) {
            boolean allEventsHaveDrivers = events.stream()
                .allMatch(e -> !e.drivers().isEmpty());
            
            if (allEventsHaveDrivers) {
                sessionCache.put(query, events);
            } else {
                log.warn("Not caching results for query {} - some events have empty driver lists", query);
            }
        }
        
        return events;
    }

    private List<EventWithDrivers> fetchDriversSequentially(List<OpenF1Session> sessions) {
        // OpenF1 API has a rate limit of 3 requests/second.
        // Add delay between driver fetches to avoid 429 errors.
        List<EventWithDrivers> results = new java.util.ArrayList<>();
        for (int i = 0; i < sessions.size(); i++) {
            if (i > 0 && driverDelayMs > 0) {
                try {
                    Thread.sleep(driverDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while fetching drivers", e);
                }
            }
            results.add(mapSessionWithDrivers(sessions.get(i)));
        }
        return results;
    }

    public List<EventWithDrivers> getSessionsFallback(SessionQuery query, Throwable t) {
        log.warn("Falling back to cached data for query: {}, error: {}", query, t.getMessage());
        List<EventWithDrivers> cached = sessionCache.getIfPresent(query);
        if (cached != null) {
            log.info("Returning stale cached data for query: {} (cache hit during outage)", query);
            return cached;
        }
        throw new ExternalServiceUnavailableException("OpenF1", 
            "F1 data service is temporarily unavailable and no cached data exists", t);
    }

    /**
     * Fallback for getSessions with skipCache parameter.
     * The skipCache parameter is intentionally ignored in fallback - when the API is down,
     * we always try to return cached data regardless of the original cache preference.
     * This signature is required by Resilience4j to match the method being decorated.
     */
    public List<EventWithDrivers> getSessionsFallbackWithSkipCache(SessionQuery query, boolean skipCache, Throwable t) {
        return getSessionsFallback(query, t);
    }

    @Override
    @CircuitBreaker(name = "openf1", fallbackMethod = "getSessionByKeyFallback")
    @Bulkhead(name = "openf1")
    @Retry(name = "openf1")
    public Optional<EventWithDrivers> getSessionByKey(int sessionKey) {
        return getSessionByKey(sessionKey, false);
    }

    /**
     * Retrieves a session by its key with optional cache bypass.
     *
     * <p><b>Cache Behavior:</b></p>
     * <ul>
     *   <li>When {@code skipCache=false} (default): Reads from cache if available, updates cache with fresh data</li>
     *   <li>When {@code skipCache=true}: Bypasses cache read AND does not update cache with the result</li>
     * </ul>
     *
     * <p><b>Why skipCache doesn't update the cache:</b></p>
     * <p>The {@code skipCache=true} option is used by {@code SettleEventUseCase} for financial decisions
     * that require fresh data. By not updating the cache, we ensure that:</p>
     * <ol>
     *   <li>Settlement gets guaranteed fresh data for the financial decision</li>
     *   <li>The cache TTL remains the authoritative source of cache freshness for normal operations</li>
     *   <li>A skipCache request doesn't reset the TTL for subsequent cached reads</li>
     * </ol>
     * <p>Subsequent {@code PlaceBetUseCase} calls (which use {@code skipCache=false}) will continue
     * using cached data until TTL expires, which is acceptable for bet placement validation.</p>
     */
    @Override
    @CircuitBreaker(name = "openf1", fallbackMethod = "getSessionByKeyFallbackWithSkipCache")
    @Bulkhead(name = "openf1")
    @Retry(name = "openf1")
    public Optional<EventWithDrivers> getSessionByKey(int sessionKey, boolean skipCache) {
        if (!skipCache) {
            Optional<EventWithDrivers> cached = sessionKeyCache.getIfPresent(sessionKey);
            if (cached != null) {
                log.debug("Returning cached session for key: {}", sessionKey);
                return cached;
            }
        } else {
            log.debug("Skipping cache for session key: {}", sessionKey);
        }

        List<OpenF1Session> sessions = client.getSessionByKey(sessionKey);

        Optional<EventWithDrivers> result;
        if (sessions.isEmpty()) {
            result = Optional.empty();
        } else {
            result = Optional.of(mapSessionWithDrivers(sessions.get(0)));
        }

        // Only update cache when not in skipCache mode - see method Javadoc for rationale
        if (!skipCache) {
            sessionKeyCache.put(sessionKey, result);
        }

        return result;
    }

    public Optional<EventWithDrivers> getSessionByKeyFallback(int sessionKey, Throwable t) {
        log.warn("Falling back to cached data for session key: {}, error: {}", sessionKey, t.getMessage());
        Optional<EventWithDrivers> cached = sessionKeyCache.getIfPresent(sessionKey);
        if (cached != null) {
            log.info("Returning stale cached data for session key: {} (cache hit during outage)", sessionKey);
            return cached;
        }
        throw new ExternalServiceUnavailableException("OpenF1",
            "F1 data service is temporarily unavailable and no cached data exists", t);
    }

    /**
     * Fallback for getSessionByKey with skipCache parameter.
     * The skipCache parameter is intentionally ignored in fallback - when the API is down,
     * we always try to return cached data regardless of the original cache preference.
     * This signature is required by Resilience4j to match the method being decorated.
     */
    public Optional<EventWithDrivers> getSessionByKeyFallbackWithSkipCache(int sessionKey, boolean skipCache, Throwable t) {
        return getSessionByKeyFallback(sessionKey, t);
    }

    private EventWithDrivers mapSessionWithDrivers(OpenF1Session session) {
        List<OpenF1Driver> drivers = fetchDriversForSession(session.sessionKey());

        List<DriverInfo> driverInfos = drivers.stream()
            .map(driver -> new DriverInfo(
                driver.driverNumber(),
                driver.fullName(),
                driver.teamName(),
                oddsCalculator.calculate(session.sessionKey(), driver.driverNumber()).getValue()
            ))
            .toList();

        return new EventWithDrivers(
            session.sessionKey(),
            session.sessionName(),
            session.sessionType(),
            session.circuitShortName(),
            session.countryName(),
            session.countryCode(),
            parseInstant(session.dateStart()),
            parseInstant(session.dateEnd()),
            session.year(),
            driverInfos
        );
    }

    private List<OpenF1Driver> fetchDriversForSession(Integer sessionKey) {
        try {
            return client.getDrivers(sessionKey);
        } catch (Exception e) {
            // Log at WARN since GlobalExceptionHandler will also log at ERROR
            log.warn("Failed to fetch drivers for session {}: {}", sessionKey, e.getMessage());
            throw new ExternalServiceUnavailableException("OpenF1",
                "Failed to fetch driver data for session " + sessionKey, e);
        }
    }

    private Instant parseInstant(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateString);
        } catch (DateTimeParseException e) {
            try {
                return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(dateString, Instant::from);
            } catch (DateTimeParseException e2) {
                log.warn("Failed to parse date: {}", dateString);
                return null;
            }
        }
    }
}
