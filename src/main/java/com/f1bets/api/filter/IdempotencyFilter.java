package com.f1bets.api.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.f1bets.api.dto.response.ErrorResponse;
import com.f1bets.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import com.f1bets.infrastructure.persistence.repository.SpringDataIdempotencyKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Servlet filter that enforces idempotency for state-changing HTTP operations.
 *
 * <h2>Purpose</h2>
 * Prevents duplicate operations from network retries, client bugs, or replay attacks.
 * All POST/PUT/PATCH requests require an {@code Idempotency-Key} header (UUID format).
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>First request: executes normally, caches response for 24 hours</li>
 *   <li>Replay (same key + same request): returns cached response</li>
 *   <li>Conflict (same key + different request): returns 409</li>
 *   <li>Concurrent (same key, in-progress): returns 409</li>
 * </ul>
 *
 * <h2>Architecture Note</h2>
 * This filter directly uses {@code SpringDataIdempotencyKeyRepository} and
 * {@code IdempotencyKeyJpaEntity}, which couples the API layer to infrastructure.
 * This is a pragmatic trade-off:
 * <ul>
 *   <li>Servlet filters are inherently infrastructure-adjacent</li>
 *   <li>Extracting an {@code IdempotencyStore} port would add complexity without clear benefit</li>
 *   <li>The filter needs direct DB access for atomicity guarantees</li>
 * </ul>
 * If provider abstraction becomes necessary (e.g., Redis-based idempotency), refactor
 * to use an {@code IdempotencyStore} interface in the application layer.
 *
 * @see <a href="https://stripe.com/docs/api/idempotent_requests">Stripe Idempotency</a>
 */
@Component
@Order(1)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Set<String> IDEMPOTENT_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final long EXPIRY_HOURS = 24;
    private static final int MAX_KEY_LENGTH = 36;
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final SpringDataIdempotencyKeyRepository idempotencyRepository;
    private final ObjectMapper canonicalMapper;
    private final ObjectMapper responseMapper;
    private final Duration staleInProgressTimeout;

    public IdempotencyFilter(
            SpringDataIdempotencyKeyRepository idempotencyRepository,
            @Value("${idempotency.stale-timeout-minutes:5}") int staleTimeoutMinutes) {
        this.idempotencyRepository = idempotencyRepository;
        this.staleInProgressTimeout = Duration.ofMinutes(staleTimeoutMinutes);
        this.canonicalMapper = new ObjectMapper();
        this.canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.responseMapper = new ObjectMapper();
        this.responseMapper.registerModule(new JavaTimeModule());
        this.responseMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!IDEMPOTENT_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Missing required Idempotency-Key header for {} {}", request.getMethod(), request.getRequestURI());
            writeErrorResponse(response, request, 400, "Bad Request",
                "Idempotency-Key header is required for " + request.getMethod() + " requests");
            return;
        }

        if (idempotencyKey.length() > MAX_KEY_LENGTH || !UUID_PATTERN.matcher(idempotencyKey).matches()) {
            log.warn("Invalid Idempotency-Key format: {}", idempotencyKey.length() > 50 ? idempotencyKey.substring(0, 50) + "..." : idempotencyKey);
            writeErrorResponse(response, request, 400, "Bad Request",
                "Idempotency-Key must be a valid UUID (e.g., 550e8400-e29b-41d4-a716-446655440000)");
            return;
        }

        String userId = request.getHeader("X-User-Id");
        
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        byte[] requestBodyBytes = wrappedRequest.getInputStream().readAllBytes();
        String requestBody = new String(requestBodyBytes, StandardCharsets.UTF_8);
        String requestHash = hashRequest(request.getMethod(), request.getRequestURI(), request.getQueryString(), requestBody, userId);

        Optional<IdempotencyKeyJpaEntity> existing = idempotencyRepository.findById(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyKeyJpaEntity cached = existing.get();
            Instant now = Instant.now();

            if (cached.getExpiresAt().isBefore(now)) {
                log.debug("Idempotency key expired, allowing reuse: {}", idempotencyKey);
                idempotencyRepository.delete(cached);
            } else if (cached.isInProgress()) {
                if (isStale(cached, now)) {
                    log.warn("Stale IN_PROGRESS idempotency key detected (created {}), treating as failed: {}",
                        cached.getCreatedAt(), idempotencyKey);
                    idempotencyRepository.delete(cached);
                } else {
                    writeConflictResponse(response, request, "Request in progress",
                        "A request with this idempotency key is already being processed");
                    return;
                }
            } else if (cached.isFailed()) {
                if (!cached.getRequestHash().equals(requestHash)) {
                    writeConflictResponse(response, request, "Idempotency key conflict",
                        "Key already used with different request");
                    return;
                }
                log.debug("Previous request failed, allowing retry for idempotency key: {}", idempotencyKey);
                idempotencyRepository.delete(cached);
            } else {
                if (!cached.getRequestHash().equals(requestHash)) {
                    writeConflictResponse(response, request, "Idempotency key conflict",
                        "Key already used with different request");
                    return;
                }
                if (userId != null && cached.getUserId() != null && !cached.getUserId().equals(userId)) {
                    writeConflictResponse(response, request, "Idempotency key conflict",
                        "Key belongs to different user");
                    return;
                }
                log.debug("Returning cached response for idempotency key: {}", idempotencyKey);
                response.setStatus(cached.getResponseStatus());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(cached.getResponsePayload());
                return;
            }
        }

        IdempotencyKeyJpaEntity inProgressEntity = IdempotencyKeyJpaEntity.createInProgress(
            idempotencyKey,
            userId,
            requestHash,
            Instant.now(),
            Instant.now().plus(EXPIRY_HOURS, ChronoUnit.HOURS)
        );

        try {
            idempotencyRepository.save(inProgressEntity);
        } catch (Exception e) {
            log.debug("Idempotency key reservation failed (concurrent request): {}", idempotencyKey);
            writeConflictResponse(response, request, "Request in progress",
                "A request with this idempotency key is already being processed");
            return;
        }

        ResettableServletInputStream resettableStream = new ResettableServletInputStream(requestBodyBytes);
        HttpServletRequest resettableRequest = new HttpServletRequestWrapper(wrappedRequest, resettableStream);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(resettableRequest, wrappedResponse);

            String responseBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            int responseStatus = wrappedResponse.getStatus();

            // CRITICAL: Send response to client FIRST, before attempting idempotency update.
            // This ensures the client sees the actual result even if DB update fails.
            wrappedResponse.copyBodyToResponse();

            // Best-effort idempotency update - don't fail the request if this fails
            try {
                inProgressEntity.markCompleted(responseBody, responseStatus);
                idempotencyRepository.save(inProgressEntity);
            } catch (Exception dbError) {
                log.error("Failed to save idempotency record for key {} after successful request (status={}). " +
                    "Idempotent replay protection may be compromised for this key.",
                    idempotencyKey, responseStatus, dbError);
                // Don't throw - the business operation succeeded, client already received response
            }
        } catch (Exception e) {
            // Business logic failed - try to mark as failed for better retry behavior
            try {
                inProgressEntity.markFailed(e.getMessage());
                idempotencyRepository.save(inProgressEntity);
            } catch (Exception dbError) {
                log.warn("Failed to mark idempotency key {} as FAILED: {}", idempotencyKey, dbError.getMessage());
            }
            throw e;
        }
    }

    private boolean isStale(IdempotencyKeyJpaEntity cached, Instant now) {
        return cached.getCreatedAt().plus(staleInProgressTimeout).isBefore(now);
    }

    private void writeConflictResponse(HttpServletResponse response, HttpServletRequest request,
                                        String error, String message) throws IOException {
        writeErrorResponse(response, request, 409, error, message);
    }

    private void writeErrorResponse(HttpServletResponse response, HttpServletRequest request,
                                    int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse errorResponse = ErrorResponse.of(status, error, message, request.getRequestURI());
        response.getWriter().write(responseMapper.writeValueAsString(errorResponse));
    }

    private String hashRequest(String method, String uri, String queryString, String body, String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String userPart = userId != null ? userId : "";
            String queryPart = queryString != null ? queryString : "";
            String canonicalBody = canonicalizeJson(body);
            String content = method + ":" + uri + ":" + queryPart + ":" + canonicalBody + ":" + userPart;
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String canonicalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode tree = canonicalMapper.readTree(json);
            return canonicalMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            log.debug("Failed to canonicalize JSON, using raw body: {}", e.getMessage());
            return json.replaceAll("\\s+", "");
        }
    }

    private static class ResettableServletInputStream extends jakarta.servlet.ServletInputStream {
        private final java.io.ByteArrayInputStream stream;

        public ResettableServletInputStream(byte[] data) {
            this.stream = new java.io.ByteArrayInputStream(data);
        }

        @Override
        public int read() { return stream.read(); }

        @Override
        public boolean isFinished() { return stream.available() == 0; }

        @Override
        public boolean isReady() { return true; }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener listener) {}
    }

    private static class HttpServletRequestWrapper extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final ResettableServletInputStream stream;

        public HttpServletRequestWrapper(HttpServletRequest request, ResettableServletInputStream stream) {
            super(request);
            this.stream = stream;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() { return stream; }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
