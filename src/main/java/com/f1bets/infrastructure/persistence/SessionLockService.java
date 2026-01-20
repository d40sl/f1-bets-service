package com.f1bets.infrastructure.persistence;

import com.f1bets.application.port.SessionLock;
import com.f1bets.domain.model.SessionKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

/**
 * PostgreSQL implementation of SessionLock using advisory locks.
 * Advisory locks are lightweight, transactional locks that don't lock any actual rows.
 */
@Service
public class SessionLockService implements SessionLock {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void acquire(SessionKey sessionKey) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
            .setParameter("key", (long) sessionKey.getValue())
            .getSingleResult();
    }
}
