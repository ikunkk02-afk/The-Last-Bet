// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** An immutable, server-created completed ledger entry. */
public record BankTransaction(UUID id, UUID accountId, UUID ownerId, Type type, long amount,
                              long balanceAfter, long timestamp, long sequence) {
    public enum Type { DEPOSIT, WITHDRAWAL }

    public BankTransaction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(type);
        if (amount <= 0 || balanceAfter < 0 || timestamp < 0 || sequence <= 0) {
            throw new IllegalArgumentException("Invalid bank transaction values");
        }
        // Also validate the implied pre-transaction balance without overflowing.
        if (type == Type.DEPOSIT) Math.subtractExact(balanceAfter, amount);
        else Math.addExact(balanceAfter, amount);
        if (type == Type.DEPOSIT && balanceAfter < amount) throw new IllegalArgumentException("Negative prior balance");
    }

    public long balanceBefore() {
        return type == Type.DEPOSIT ? Math.subtractExact(balanceAfter, amount) : Math.addExact(balanceAfter, amount);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("account", accountId);
        tag.putUUID("owner", ownerId);
        tag.putString("type", type.name());
        tag.putLong("amount", amount);
        tag.putLong("balance_after", balanceAfter);
        tag.putLong("timestamp", timestamp);
        tag.putLong("sequence", sequence);
        return tag;
    }

    public static BankTransaction load(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("account") || !tag.hasUUID("owner")
                || !tag.contains("type", Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid transaction identity");
        for (String key : new String[]{"amount", "balance_after", "timestamp", "sequence"}) {
            if (!tag.contains(key, Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid transaction " + key);
        }
        return new BankTransaction(tag.getUUID("id"), tag.getUUID("account"), tag.getUUID("owner"),
                Type.valueOf(tag.getString("type")), tag.getLong("amount"), tag.getLong("balance_after"),
                tag.getLong("timestamp"), tag.getLong("sequence"));
    }
}
