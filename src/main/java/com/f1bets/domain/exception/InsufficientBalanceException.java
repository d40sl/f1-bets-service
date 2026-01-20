package com.f1bets.domain.exception;

import com.f1bets.domain.model.Money;

public class InsufficientBalanceException extends RuntimeException {

    private final Money currentBalance;
    private final Money requiredAmount;

    public InsufficientBalanceException(Money currentBalance, Money requiredAmount) {
        super("Insufficient balance: current=" + currentBalance + ", required=" + requiredAmount);
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }

    public Money getCurrentBalance() {
        return currentBalance;
    }

    public Money getRequiredAmount() {
        return requiredAmount;
    }
}
