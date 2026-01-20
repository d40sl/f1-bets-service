package com.f1bets.application.port;

import com.f1bets.domain.model.SessionKey;

/**
 * Port for acquiring session-level locks to prevent concurrent operations on the same session.
 * Used to ensure atomicity of bet placement and settlement operations.
 */
public interface SessionLock {

    /**
     * Acquire an exclusive lock for the given session.
     * The lock is automatically released when the current transaction ends.
     *
     * @param sessionKey the session to lock
     */
    void acquire(SessionKey sessionKey);
}
