package com.f1bets.infrastructure.config;

import com.f1bets.infrastructure.persistence.repository.SpringDataIdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final SpringDataIdempotencyKeyRepository idempotencyRepository;
    private final Duration staleInProgressTimeout;

    public ScheduledTasks(
            SpringDataIdempotencyKeyRepository idempotencyRepository,
            @Value("${idempotency.stale-timeout-minutes:5}") int staleTimeoutMinutes) {
        this.idempotencyRepository = idempotencyRepository;
        this.staleInProgressTimeout = Duration.ofMinutes(staleTimeoutMinutes);
    }

    @Scheduled(cron = "${idempotency.cleanup.cron:0 0 * * * *}")
    @Transactional
    public void cleanupExpiredIdempotencyKeys() {
        Instant now = Instant.now();
        
        int expiredDeleted = idempotencyRepository.deleteExpiredKeys(now);
        if (expiredDeleted > 0) {
            log.info("Cleaned up {} expired idempotency keys", expiredDeleted);
        }

        Instant staleThreshold = now.minus(staleInProgressTimeout);
        int staleDeleted = idempotencyRepository.deleteStaleInProgressKeys(staleThreshold);
        if (staleDeleted > 0) {
            log.warn("Cleaned up {} stale IN_PROGRESS idempotency keys (older than {})", 
                staleDeleted, staleInProgressTimeout);
        }
    }
}
