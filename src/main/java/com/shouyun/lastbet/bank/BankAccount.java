// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import java.util.Objects;
import java.util.UUID;

/** Immutable server-owned account view. Amounts are whole emeralds. */
public record BankAccount(UUID ownerId, UUID accountId, long balance, UUID cardId, boolean deliveryPending) {
    public BankAccount {
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(cardId);
        if (balance < 0) throw new IllegalArgumentException("Negative bank balance");
    }

    BankAccount delivered() {
        return new BankAccount(ownerId, accountId, balance, cardId, false);
    }

    public boolean matches(UUID owner, UUID account, UUID card) {
        return ownerId.equals(owner) && accountId.equals(account) && cardId.equals(card);
    }
}
