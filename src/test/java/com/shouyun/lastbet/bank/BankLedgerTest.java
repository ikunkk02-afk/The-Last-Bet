// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BankLedgerTest {
    @TempDir Path directory;
    @BeforeAll static void version() { SharedConstants.tryDetectVersion(); }
    private BankSavedData data() {
        BankSavedData data = new BankSavedData();
        var a = data.create(UUID.randomUUID()); data.finishDelivery(a.ownerId());
        return data;
    }
    private BankTransaction deposit(BankSavedData data, long amount, long time) {
        var a = data.accounts().iterator().next();
        return new BankTransaction(UUID.randomUUID(), a.accountId(), a.ownerId(), BankTransaction.Type.DEPOSIT,
                amount, Math.addExact(a.balance(), amount), time, data.history(a.ownerId()).size() + 1L);
    }
    @Test void migrationPreservesNonzeroBalanceAndAllIdentifiersAcrossSaveReload() throws Exception {
        var original = data();
        var tag = original.save(new CompoundTag(), null);
        tag.putInt("schema_version", 1); tag.remove("transactions");
        var entry = tag.getList("accounts", 10).getCompound(0); entry.putLong("balance", 1234567890123L);
        var migrated = BankSavedData.load(tag, null);
        var a = migrated.accounts().iterator().next();
        assertEquals(entry.getUUID("account"), a.accountId()); assertEquals(entry.getUUID("card"), a.cardId());
        assertEquals(entry.getUUID("owner"), a.ownerId()); assertEquals(1234567890123L, a.balance());
        assertTrue(migrated.history(a.ownerId()).isEmpty()); assertNull(migrated.checkpoint(a.ownerId()));
        Path file = directory.resolve("bank.dat"); migrated.saveChecked(file, null);
        var reloaded = BankSavedData.load(BankPersistence.read(file).getCompound("data"), null);
        assertEquals(a, reloaded.accounts().iterator().next());
        assertEquals(2, BankPersistence.read(file).getCompound("data").getInt("schema_version"));
    }
    @Test void ledgerIsIdempotentAndSurvivesReloadWithBackwardClock() {
        var data = data(); var first = deposit(data, 16, 2000); data.commit(first); data.commit(first);
        var second = deposit(data, 64, 1000); data.commit(second);
        var loaded = BankSavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(java.util.List.of(first, second), loaded.history(first.ownerId()));
        assertEquals(second.id(), loaded.checkpoint(first.ownerId())); assertEquals(80, loaded.findByOwner(first.ownerId()).orElseThrow().balance());
        assertThrows(UnsupportedOperationException.class, () -> loaded.history(first.ownerId()).clear());
    }
    @Test void rejectsConflictingDuplicateAndIncorrectOwnerSequenceOrBalance() {
        var data = data(); var first = deposit(data, 16, 1000); data.commit(first);
        assertThrows(IllegalArgumentException.class, () -> data.commit(new BankTransaction(first.id(), first.accountId(), first.ownerId(), first.type(), 1, 1, 1000, 1)));
        assertThrows(IllegalArgumentException.class, () -> data.commit(new BankTransaction(UUID.randomUUID(), first.accountId(), first.ownerId(), first.type(), 1, 17, 1000, 4)));
        assertThrows(IllegalArgumentException.class, () -> data.commit(new BankTransaction(UUID.randomUUID(), first.accountId(), UUID.randomUUID(), first.type(), 1, 1, 1000, 1)));
        assertEquals(16, data.findByOwner(first.ownerId()).orElseThrow().balance());
    }
    @Test void invalidLedgerAndCheckpointRejectWholeFile() {
        for (String key : new String[]{"owner", "account", "id", "amount", "sequence", "balance_after"}) {
            var data = data(); var first = deposit(data, 16, 1000); data.commit(first);
            var tag = data.save(new CompoundTag(), null); tag.getList("transactions", 10).getCompound(0).remove(key);
            assertThrows(RuntimeException.class, () -> BankSavedData.load(tag, null), key);
        }
        var data = data(); data.commit(deposit(data, 1, 1000));
        var tag = data.save(new CompoundTag(), null); tag.getList("accounts", 10).getCompound(0).putUUID("checkpoint", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> BankSavedData.load(tag, null));
    }
    @Test void rejectsZeroNegativeAndOverflowingTransactionArithmetic() {
        var data = data(); var a = data.accounts().iterator().next();
        for (long amount : new long[]{0, -1}) assertThrows(IllegalArgumentException.class, () -> new BankTransaction(UUID.randomUUID(), a.accountId(), a.ownerId(), BankTransaction.Type.DEPOSIT, amount, 0, 1, 1));
        assertThrows(ArithmeticException.class, () -> new BankTransaction(UUID.randomUUID(), a.accountId(), a.ownerId(), BankTransaction.Type.WITHDRAWAL, Long.MAX_VALUE, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BankTransaction(UUID.randomUUID(), a.accountId(), a.ownerId(), BankTransaction.Type.DEPOSIT, 2, 1, 1, 1));
    }
    @Test void preservesCompleteHistoryBeyondTenEntries() {
        var data = data(); for (int i = 0; i < 35; i++) data.commit(deposit(data, 1, i));
        var loaded = BankSavedData.load(data.save(new CompoundTag(), null), null);
        var a = loaded.accounts().iterator().next(); assertEquals(35, loaded.history(a.ownerId()).size());
        assertEquals(35, a.balance());
    }
}
