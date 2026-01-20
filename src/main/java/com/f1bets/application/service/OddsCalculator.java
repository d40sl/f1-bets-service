package com.f1bets.application.service;

import com.f1bets.domain.model.Odds;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Calculates deterministic odds based on session key and driver number.
 *
 * Odds are derived from hash(sessionKey, driverNumber, seed). The seed is
 * configurable via the odds.seed property. For audit/dispute resolution,
 * the seed hash is logged at startup.
 */
@Service
public class OddsCalculator {

    private static final Logger log = LoggerFactory.getLogger(OddsCalculator.class);

    private final String seed;

    public OddsCalculator(@Value("${odds.seed:F1BETS_SEED}") String seed) {
        this.seed = seed;
    }

    @PostConstruct
    public void logSeedConfiguration() {
        // Log seed hash for audit trail - actual seed value is not logged for security
        log.info("OddsCalculator initialized - seed hash: {} (for audit verification)",
            Integer.toHexString(seed.hashCode()));
    }

    public Odds calculate(int sessionKey, int driverNumber) {
        return Odds.fromSessionAndDriver(sessionKey, driverNumber, seed);
    }
}
