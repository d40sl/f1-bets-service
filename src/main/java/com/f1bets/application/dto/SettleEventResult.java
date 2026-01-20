package com.f1bets.application.dto;

import com.f1bets.domain.model.Money;

/**
 * Result of settling an F1 event.
 */
public record SettleEventResult(
    int sessionKey,
    int winningDriverNumber,
    int totalBets,
    int winningBets,
    Money totalPayout
) {}
