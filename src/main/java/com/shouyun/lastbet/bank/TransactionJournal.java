// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Recovery evidence. The PREPARED record must be durable before touching inventory. */
public record TransactionJournal(BankTransaction transaction, UUID previousCheckpoint,
                                 ListTag before, ListTag after, boolean committed) {
    public static final String CHECKPOINT = "lastbet_transaction";

    public TransactionJournal {
        Objects.requireNonNull(transaction);
        before = before.copy();
        after = after.copy();
    }

    public TransactionJournal completed() { return new TransactionJournal(transaction, previousCheckpoint, before, after, true); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", 1);
        tag.putString("state", committed ? "COMMITTED" : "PREPARED");
        tag.put("transaction", transaction.save());
        if (previousCheckpoint != null) tag.putUUID("previous_checkpoint", previousCheckpoint);
        tag.put("before", before.copy());
        tag.put("after", after.copy());
        return tag;
    }

    public static TransactionJournal load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("schema_version", Tag.TAG_INT) || tag.getInt("schema_version") != 1
                || !tag.contains("state", Tag.TAG_STRING) || !tag.contains("transaction", Tag.TAG_COMPOUND)
                || !tag.contains("before", Tag.TAG_LIST) || !tag.contains("after", Tag.TAG_LIST)
                || (tag.contains("previous_checkpoint") && !tag.hasUUID("previous_checkpoint"))) {
            throw new IllegalArgumentException("Invalid transaction journal");
        }
        String state = tag.getString("state");
        if (!state.equals("PREPARED") && !state.equals("COMMITTED")) throw new IllegalArgumentException("Unknown journal state");
        var transaction = BankTransaction.load(tag.getCompound("transaction"));
        var before = (ListTag) tag.get("before");
        var after = (ListTag) tag.get("after");
        if (!EmeraldInventory.plan(before, transaction.type(), transaction.amount(), registries).equals(after)) {
            throw new IllegalArgumentException("Journal inventory delta does not match transaction");
        }
        return new TransactionJournal(transaction, tag.hasUUID("previous_checkpoint") ? tag.getUUID("previous_checkpoint") : null,
                before, after, state.equals("COMMITTED"));
    }

    public static UUID checkpoint(CompoundTag tag) {
        if (tag.contains(CHECKPOINT) && !tag.hasUUID(CHECKPOINT)) throw new IllegalArgumentException("Invalid player checkpoint");
        return tag.hasUUID(CHECKPOINT) ? tag.getUUID(CHECKPOINT) : null;
    }
}
